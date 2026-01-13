
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Random;
import javax.sound.sampled.*;

public class GamePanel extends JPanel implements ActionListener, KeyListener, MouseListener, MouseMotionListener {
    private final static int WIDTH = 600;
    private final static int HEIGHT = 800;
    private final static int GROUND_HEIGHT = 54;
    private final static int POLE_WIDTH = 300;
    private final static int POLE_HEIGHT = 600;
    private final static int POLE_OVERLAP = 70;
    private final static double GRAVITY = 0.2;
    private final static double JUMP_VELOCITY = -4;
    private final static int OBSTACLE_SPEED = 3;
    private final static int MAX_OBSTACLE_SPEED = 12;
    private final static int BIRD_WIDTH = 320;
    private final static int BIRD_ONLY_WIDTH = 320, BIRD_ONLY_HEIGHT = 240;
    private final static int BIRD_PAIR_HEIGHT = 240;       
    private final static int MIN_OBSTACLE_HEIGHT = 200, MAX_OBSTACLE_HEIGHT = 350; // (no longer used for pair)
    private final static int WAW_WIDTH = 130, WAW_HEIGHT = 130;
    private int x = 100, y = 300;
    private double velocity = 0;
    private Timer timer;
    private Image wauImage, bgImg, birdImg, poleImg, groundImg;
    private boolean gameOver = false;
    private boolean gameStarted = false;
    private boolean readyToStart = false;
    private boolean paused = false;
    private boolean muted = false;
    private int score = 0;
    private int highScore = 0;
    private final Random random = new Random();
    private Clip jumpSound, hitSound, scoreSound, bgMusic;
    private int menuAnimationCounter = 0;
    private Rectangle hoveredButton = null;
    private Rectangle startButton = new Rectangle(150, 350, 300, 80);
    private Rectangle wauButton = new Rectangle(150, 450, 300, 80);
    private Rectangle restartButton = new Rectangle(150, 420, 300, 80);
    private Rectangle wauBackButton = new Rectangle(80, 695, 180, 60);
    private Rectangle wauSelectButton = new Rectangle(340, 695, 180, 60);
    private boolean showWauSelection = false;
    private WauType currentWauType;
    private ArrayList<Rectangle> wauSelectionBoxes = new ArrayList<>();
    private int wauScrollOffset = 0;
    private int wauMaxScroll = 0;
    private final int WAU_BOX_SIZE = 120;
    private final int WAU_BOX_GAP = 30;
    private int wauSelectedIndex = 0;
    private boolean showWauDetails = false;
    private int wauDetailScrollOffset = 0;
    private int wauDetailScrollMax = 0;
    private final int POLE_TOP_CUT = 100;
    private final int POLE_BOTTOM_CUT = 30;

    // ==== Score bump animation ====
    private int scoreBumpTimer = 0;
    private static final int SCORE_BUMP_DURATION = 10;

    enum ObstacleType { BIRD_ONLY, POLE_ONLY, BOTH }
    class ObstaclePair {
        Rectangle birdRect;
        Rectangle poleRect;
        public ObstaclePair(Rectangle bird, Rectangle pole) {
            this.birdRect = bird;
            this.poleRect = pole;
        }
    }
    private ArrayList<ObstaclePair> obstacles = new ArrayList<>();

    public GamePanel(WauType type) {
        this.setPreferredSize(new Dimension(WIDTH, HEIGHT));
        this.setFocusable(true);
        this.addKeyListener(this);
        this.addMouseListener(this);
        this.addMouseMotionListener(this);
        this.addMouseWheelListener(new MouseWheelListener() {
            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                if (showWauSelection) {
                    if (showWauDetails) {
                        wauDetailScrollOffset += e.getWheelRotation() * 30;
                        if (wauDetailScrollOffset < 0) wauDetailScrollOffset = 0;
                        if (wauDetailScrollOffset > wauDetailScrollMax) wauDetailScrollOffset = wauDetailScrollMax;
                        repaint();
                    } else {
                        int wauTypesCount = WauType.values().length;
                        int rows = (int)Math.ceil(wauTypesCount / 3.0);
                        int visibleRows = 2; // Show 2 rows at a time
                        wauMaxScroll = Math.max(0, (rows - visibleRows) * (WAU_BOX_SIZE + WAU_BOX_GAP + 25));
                        wauScrollOffset += e.getWheelRotation() * 40;
                        if (wauScrollOffset < 0) wauScrollOffset = 0;
                        if (wauScrollOffset > wauMaxScroll) wauScrollOffset = wauMaxScroll;
                        repaint();
                    }
                }
            }
        });
        this.currentWauType = type;
        loadImages(type);
        initializeWauSelectionBoxes();
        loadSounds();
        timer = new Timer(10, this);
        timer.start();
        syncSelectedIndexWithCurrent();
    }

    private void syncSelectedIndexWithCurrent() {
        WauType[] wauTypes = WauType.values();
        for (int i = 0; i < wauTypes.length; i++) {
            if (wauTypes[i] == currentWauType) {
                wauSelectedIndex = i;
                break;
            }
        }
    }

    private void drawWauDetailsPage(Graphics2D g2, Graphics g, WauType selectedWau) {
        int topX = 60;
        int topY = 140;
        int topW = WIDTH - 120;
        int topH = 220;

        g.setColor(new Color(255, 255, 255, 35));
        g.fillRoundRect(topX, topY, topW, topH, 28, 28);
        g2.setColor(new Color(255, 215, 0));
        g2.setStroke(new BasicStroke(4));
        g2.drawRoundRect(topX, topY, topW, topH, 28, 28);

        int imgSize = 160;
        int imgX = topX + (topW - imgSize) / 2;
        int imgY = topY + 20;
        try {
            Image wauImg = new ImageIcon(getClass().getClassLoader().getResource(selectedWau.image)).getImage();
            g.drawImage(wauImg, imgX, imgY, imgSize, imgSize, null);
        } catch (Exception e) {
            g.setColor(Color.GRAY);
            g.fillRoundRect(imgX, imgY, imgSize, imgSize, 16, 16);
        }

        g.setFont(new Font("Arial", Font.BOLD, 32));
        g.setColor(Color.WHITE);
        FontMetrics fm = g.getFontMetrics();
        String name = selectedWau.getDisplayName();
        g.drawString(name, topX + (topW - fm.stringWidth(name)) / 2, topY + topH - 25);

        int bottomX = topX;
        int bottomY = topY + topH + 30;
        int bottomW = topW;
        int bottomH = 300;

        g.setColor(new Color(255, 255, 255, 28));
        g.fillRoundRect(bottomX, bottomY, bottomW, bottomH, 28, 28);
        g2.setColor(new Color(200, 200, 200));
        g2.setStroke(new BasicStroke(3));
        g2.drawRoundRect(bottomX, bottomY, bottomW, bottomH, 28, 28);

        g.setFont(new Font("Arial", Font.BOLD, 24));
        g.setColor(Color.WHITE);
        g.drawString("History & Description", bottomX + 24, bottomY + 40);

        g.setFont(new Font("Arial", Font.PLAIN, 16));
        g.setColor(Color.WHITE);
        FontMetrics bodyMetrics = g.getFontMetrics();
        int lineHeight = 20;
        int visibleHeight = bottomH - 70;
        int contentHeight = computeWrappedHeight(selectedWau.getDetails(), bodyMetrics, bottomW - 48, lineHeight);
        wauDetailScrollMax = Math.max(0, contentHeight - visibleHeight);
        if (wauDetailScrollOffset < 0) wauDetailScrollOffset = 0;
        if (wauDetailScrollOffset > wauDetailScrollMax) wauDetailScrollOffset = wauDetailScrollMax;

        Shape oldClip = g.getClip();
        g.setClip(bottomX + 18, bottomY + 50, bottomW - 36, visibleHeight);
        drawWrappedString(g, selectedWau.getDetails(), bottomX + 24, bottomY + 70 - wauDetailScrollOffset, bottomW - 48, lineHeight);
        g.setClip(oldClip);
    }

    private int drawWrappedString(Graphics g, String text, int x, int y, int maxWidth, int lineHeight) {
        FontMetrics fm = g.getFontMetrics();
        int currentY = y;
        String[] lines = text.split("\\n");

        for (String rawLine : lines) {
            if (rawLine.trim().isEmpty()) {
                currentY += lineHeight;
                continue;
            }

            String lineText = rawLine.trim();
            boolean bullet = lineText.startsWith("-");
            if (bullet) {
                lineText = lineText.substring(1).trim();
            }

            StringBuilder line = new StringBuilder();
            if (bullet) {
                line.append("- ");
            }

            for (String word : lineText.split(" ")) {
                if (word.isEmpty()) continue;
                String candidate = line.length() == 0 ? word : line.toString() + " " + word;
                if (fm.stringWidth(candidate) > maxWidth) {
                    g.drawString(line.toString(), x, currentY);
                    currentY += lineHeight;
                    line = new StringBuilder();
                    if (bullet) {
                        line.append("  ");
                    }
                    line.append(word);
                } else {
                    if (line.length() > 0) line.append(" ");
                    line.append(word);
                }
            }

            if (line.length() > 0) {
                g.drawString(line.toString(), x, currentY);
                currentY += lineHeight;
            }
        }

        return currentY;
    }

    private int computeWrappedHeight(String text, FontMetrics fm, int maxWidth, int lineHeight) {
        int height = 0;
        String[] lines = text.split("\\n");

        for (String rawLine : lines) {
            if (rawLine.trim().isEmpty()) {
                height += lineHeight;
                continue;
            }

            String lineText = rawLine.trim();
            boolean bullet = lineText.startsWith("-");
            if (bullet) {
                lineText = lineText.substring(1).trim();
            }

            StringBuilder line = new StringBuilder();
            if (bullet) {
                line.append("- ");
            }

            for (String word : lineText.split(" ")) {
                if (word.isEmpty()) continue;
                String candidate = line.length() == 0 ? word : line.toString() + " " + word;
                if (fm.stringWidth(candidate) > maxWidth) {
                    height += lineHeight;
                    line = new StringBuilder();
                    if (bullet) {
                        line.append("  ");
                    }
                    line.append(word);
                } else {
                    if (line.length() > 0) line.append(" ");
                    line.append(word);
                }
            }

            if (line.length() > 0) {
                height += lineHeight;
            }
        }

        return height;
    }

    void initializeWauSelectionBoxes() {
        wauSelectionBoxes.clear();
        int startX = 80;
        int startY = 360; // Lower to create clear gap below the preview header
        for (int i = 0; i < WauType.values().length; i++) {
            int col = i % 3;
            int row = i / 3;
            int x = startX + col * (WAU_BOX_SIZE + WAU_BOX_GAP);
            int y = startY + row * (WAU_BOX_SIZE + WAU_BOX_GAP + 25);
            wauSelectionBoxes.add(new Rectangle(x, y, WAU_BOX_SIZE, WAU_BOX_SIZE));
        }
    }

    void loadImages(WauType type) {
        try {
            ClassLoader cl = getClass().getClassLoader();
            wauImage = new ImageIcon(cl.getResource(type.image)).getImage();
            bgImg = new ImageIcon(cl.getResource("background.png")).getImage();
            groundImg = new ImageIcon(cl.getResource("ground.png")).getImage();
            birdImg = new ImageIcon(cl.getResource("birds.png")).getImage();
            poleImg = new ImageIcon(cl.getResource("coconut.png")).getImage();
        } catch (Exception e) {
            System.err.println("Image loading failed: " + e.getMessage());
        }
    }

    void loadSounds() {
        jumpSound = loadClip("jump.wav");
        hitSound = loadClip("hit.wav");
        scoreSound = loadClip("score.wav");
        bgMusic = loadClip("music.wav");
    }

    Clip loadClip(String path) {
        try {
            AudioInputStream ais = AudioSystem.getAudioInputStream(
                getClass().getClassLoader().getResource(path)
            );
            Clip clip = AudioSystem.getClip();
            clip.open(ais);
            return clip;
        } catch (Exception e) {
            System.err.println("Failed to load sound: " + path + " - " + e);
            return null;
        }
    }

    void playSound(Clip clip) {
        if (clip == null || muted) return;
        if (clip.isRunning()) clip.stop();
        clip.setFramePosition(0);
        clip.start();
    }

    void spawnObstacles() {
        ObstacleType type;
        int rand = random.nextInt(3);

        if (rand == 0) type = ObstacleType.BIRD_ONLY;
        else if (rand == 1) type = ObstacleType.POLE_ONLY;
        else type = ObstacleType.BOTH;

        int minY = 50;
        int maxBirdY = HEIGHT - GROUND_HEIGHT - BIRD_ONLY_HEIGHT - 50;

        if (type == ObstacleType.BIRD_ONLY) {
            int yBird = random.nextInt(maxBirdY - minY + 1) + minY;
            Rectangle birdRect = new Rectangle(WIDTH, yBird, BIRD_ONLY_WIDTH, BIRD_ONLY_HEIGHT);
            obstacles.add(new ObstaclePair(birdRect, null));
        } else if (type == ObstacleType.POLE_ONLY) {
            int poleY = HEIGHT - GROUND_HEIGHT - POLE_HEIGHT + POLE_OVERLAP;
            Rectangle poleRect = new Rectangle(WIDTH, poleY, POLE_WIDTH, POLE_HEIGHT + POLE_OVERLAP);
            obstacles.add(new ObstaclePair(null, poleRect));
        } else { // Paired, now with STATIC height for bird part
            int birdHeight = BIRD_PAIR_HEIGHT; // << This is now static!
            int gap = random.nextInt(81) + 40;
            Rectangle birdRect = new Rectangle(WIDTH, 0, BIRD_WIDTH, birdHeight);
            Rectangle poleRect = new Rectangle(WIDTH, birdHeight + gap, POLE_WIDTH, POLE_HEIGHT);
            obstacles.add(new ObstaclePair(birdRect, poleRect));
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (!gameStarted || gameOver || paused) return;
        menuAnimationCounter++;
        velocity += GRAVITY;
        y += velocity;

        int speed = Math.min(OBSTACLE_SPEED + score / 5, MAX_OBSTACLE_SPEED);

        for (int i = 0; i < obstacles.size(); i++) {
            ObstaclePair op = obstacles.get(i);
            if (op.birdRect != null) op.birdRect.x -= speed;
            if (op.poleRect != null) op.poleRect.x -= speed;
            if (op.birdRect != null && op.birdRect.x + op.birdRect.width < x && op.birdRect.x + op.birdRect.width + speed >= x) {
                score++;
                scoreBumpTimer = SCORE_BUMP_DURATION; // <-- bump animation
                playSound(scoreSound);
                if (!muted && bgMusic != null) bgMusic.start();
            }
            if (op.poleRect != null && op.poleRect.x + POLE_WIDTH < x && op.poleRect.x + POLE_WIDTH + speed >= x) {
                score++;
                scoreBumpTimer = SCORE_BUMP_DURATION; // <-- bump animation
                playSound(scoreSound);
                if (!muted && bgMusic != null) bgMusic.start();
            }
        }

        if (!obstacles.isEmpty()) {
            ObstaclePair op = obstacles.get(0);
            boolean birdOff = (op.birdRect == null) || (op.birdRect.x < - (op.birdRect != null ? op.birdRect.width : 0));
            boolean poleOff = (op.poleRect == null) || (op.poleRect.x < -POLE_WIDTH);
            if (birdOff && poleOff) {
                obstacles.remove(0);
                spawnObstacles();
            }
        }
        checkCollision();
        repaint();
    }

    void checkCollision() {
        int wauInsetX = 45, wauInsetY = 44;
        Rectangle wau = new Rectangle(
            x + wauInsetX, y + wauInsetY,
            WAW_WIDTH - 2 * wauInsetX, WAW_HEIGHT - 2 * wauInsetY
        );
        for (ObstaclePair op : obstacles) {
            if (op.birdRect != null) {
                int birdInsetX = op.birdRect.width / 3, birdInsetY = 30;
                Rectangle birdHitbox = new Rectangle(
                    op.birdRect.x + birdInsetX, op.birdRect.y + birdInsetY,
                    op.birdRect.width - 2 * birdInsetX, op.birdRect.height - 2 * birdInsetY);
                if (wau.intersects(birdHitbox)) {
                    playSound(hitSound);
                    gameOver = true;
                    if (bgMusic != null) bgMusic.stop();
                    return;
                }
            }
            if (op.poleRect != null) {
                int poleInsetX = op.poleRect.width / 3;
                Rectangle poleHitbox = new Rectangle(
                    op.poleRect.x + poleInsetX,
                    op.poleRect.y + POLE_TOP_CUT,
                    op.poleRect.width - 2 * poleInsetX,
                    op.poleRect.height - POLE_TOP_CUT - POLE_BOTTOM_CUT
                );
                if (wau.intersects(poleHitbox)) {
                    playSound(hitSound);
                    gameOver = true;
                    if (bgMusic != null) bgMusic.stop();
                    return;
                }
            }
        }
        if (y < 0 || y + WAW_HEIGHT > HEIGHT - GROUND_HEIGHT) {
            playSound(hitSound);
            gameOver = true;
            if (bgMusic != null) bgMusic.stop();
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        g.setColor(new Color(135, 206, 235));
        g.fillRect(0, 0, WIDTH, HEIGHT);
        if (bgImg != null) {
            g.drawImage(bgImg, 0, 0, WIDTH, HEIGHT, null);
        }
        Graphics2D g2 = (Graphics2D) g;

        for (ObstaclePair op : obstacles) {
            if (op.birdRect != null) {
                if (birdImg != null)
                    g.drawImage(birdImg, op.birdRect.x, op.birdRect.y, op.birdRect.width, op.birdRect.height, null);
                else {
                    g.setColor(Color.RED);
                    g.fillRect(op.birdRect.x, op.birdRect.y, op.birdRect.width, op.birdRect.height);
                }
                int birdInsetX = op.birdRect.width / 3, birdInsetY = 30;
            }
            if (op.poleRect != null) {
                if (poleImg != null)
                    g.drawImage(poleImg, op.poleRect.x, op.poleRect.y, POLE_WIDTH, op.poleRect.height, null);
                else {
                    g.setColor(Color.GREEN);
                    g.fillRect(op.poleRect.x, op.poleRect.y, POLE_WIDTH, op.poleRect.height);
                }
                int poleInsetX = op.poleRect.width / 3;
            }
        }

        // Wau falling/jumping animation: rotation based on velocity
        double angle = Math.toRadians(Math.max(-10, Math.min(40, velocity * 10)));
        Graphics2D g2d = (Graphics2D) g.create();
        int cx = x + WAW_WIDTH / 2;
        int cy = y + WAW_HEIGHT / 2;
        g2d.rotate(angle, cx, cy);
        g2d.drawImage(wauImage, x, y, WAW_WIDTH, WAW_HEIGHT, null);
        g2d.dispose();

        int groundY = HEIGHT - GROUND_HEIGHT;
        g.drawImage(groundImg, 0, groundY, WIDTH, GROUND_HEIGHT, null);


        // ==== SCORE BUMP ANIMATION ====
        int fontSize = 30;
        if (scoreBumpTimer > 0) {
            fontSize = 42;
            scoreBumpTimer--;
        }
        g.setFont(new Font("Arial", Font.BOLD, fontSize));
        g.setColor(Color.BLACK);
        g.drawString("Score: " + score, 22, 42);
        g.setColor(Color.WHITE);
        g.drawString("Score: " + score, 20, 40);

        if (muted) {
            g.setFont(new Font("Arial", Font.BOLD, 18));
            g.setColor(Color.BLACK);
            g.drawString("MUTED", WIDTH - 110, 42);
            g.setColor(Color.WHITE);
            g.drawString("MUTED", WIDTH - 112, 40);
        }

        if (showWauSelection) {
            g.setColor(new Color(0, 0, 0, 220));
            g.fillRect(0, 0, WIDTH, HEIGHT);
            WauType[] wauTypes = WauType.values();
            WauType selectedWau = wauTypes[wauSelectedIndex];
            g.setFont(new Font("Arial", Font.BOLD, 38));
            g.setColor(new Color(255, 215, 0));
            g.drawString("SELECT YOUR WAU", 110, 35);
            if (showWauDetails) {
                wauDetailScrollOffset = Math.max(0, Math.min(wauDetailScrollOffset, wauDetailScrollMax));
                drawWauDetailsPage(g2, g, selectedWau);
            } else {
                int previewBoxSize = 180;
                int previewBoxX = (WIDTH - previewBoxSize) / 2;
                int previewBoxY = 60;
                g.setColor(new Color(255, 255, 255, 30));
                g.fillRoundRect(previewBoxX, previewBoxY, previewBoxSize, previewBoxSize, 24, 24);
                g.setColor(new Color(255, 215, 0));
                g2.setStroke(new BasicStroke(4));
                g2.drawRoundRect(previewBoxX, previewBoxY, previewBoxSize, previewBoxSize, 24, 24);
                try {
                    Image wauImg = new ImageIcon(getClass().getClassLoader().getResource(selectedWau.image)).getImage();
                    int imgMargin = 18;
                    g.drawImage(wauImg, previewBoxX + imgMargin, previewBoxY + imgMargin, previewBoxSize - 2 * imgMargin, previewBoxSize - 2 * imgMargin, null);
                } catch (Exception e) {
                    g.setColor(Color.GRAY);
                    g.fillRoundRect(previewBoxX + 18, previewBoxY + 18, previewBoxSize - 36, previewBoxSize - 36, 10, 10);
                }
                g.setFont(new Font("Arial", Font.BOLD, 28));
                g.setColor(Color.WHITE);
                String wauName = selectedWau.getDisplayName();
                FontMetrics fm = g.getFontMetrics();
                int textX = previewBoxX + (previewBoxSize - fm.stringWidth(wauName)) / 2;
                g.drawString(wauName, textX, previewBoxY + previewBoxSize + 48);
                int gridTop = previewBoxY + previewBoxSize + 80;
                int gridHeight = 320;
                Shape oldClip = g.getClip();
                g.setClip(40, gridTop, WIDTH - 80, gridHeight);
                for (int i = 0; i < wauTypes.length; i++) {
                    Rectangle box = wauSelectionBoxes.get(i);
                    int drawY = box.y - wauScrollOffset;
                    if (drawY + box.height < gridTop || drawY > gridTop + gridHeight) continue;
                    Rectangle drawBox = new Rectangle(box.x, drawY, box.width, box.height);
                    if (i == wauSelectedIndex) {
                        g.setColor(new Color(255, 215, 0, 100));
                        g.fillRoundRect(drawBox.x - 8, drawBox.y - 8, drawBox.width + 16, drawBox.height + 16, 20, 20);
                        g2.setColor(new Color(255, 215, 0));
                        g2.setStroke(new BasicStroke(5));
                        g2.drawRoundRect(drawBox.x - 5, drawBox.y - 5, drawBox.width + 10, drawBox.height + 10, 15, 15);
                    }
                    g.setColor(Color.WHITE);
                    g2.setStroke(new BasicStroke(3));
                    g2.drawRoundRect(drawBox.x, drawBox.y, drawBox.width, drawBox.height, 10, 10);
                    try {
                        Image wauImg = new ImageIcon(getClass().getClassLoader().getResource(wauTypes[i].image)).getImage();
                        g.drawImage(wauImg, drawBox.x + 10, drawBox.y + 10, drawBox.width - 20, drawBox.height - 20, null);
                    } catch (Exception e) {
                        g.setColor(Color.GRAY);
                        g.fillRoundRect(drawBox.x + 10, drawBox.y + 10, drawBox.width - 20, drawBox.height - 20, 5, 5);
                    }
                    g.setFont(new Font("Arial", Font.PLAIN, 14));
                    g.setColor(Color.WHITE);
                    String name = wauTypes[i].getDisplayName();
                    FontMetrics fm2 = g.getFontMetrics();
                    int tX = drawBox.x + (drawBox.width - fm2.stringWidth(name)) / 2;
                    g.drawString(name, tX, drawBox.y + drawBox.height + 20);
                }
                g.setClip(oldClip);
            }
            boolean backHovered = hoveredButton == wauBackButton;
            g.setColor(backHovered ? new Color(220, 80, 80, 240) : new Color(200, 50, 50, 220));
            g.fillRoundRect(wauBackButton.x, wauBackButton.y, wauBackButton.width, wauBackButton.height, 20, 20);
            g.setColor(Color.WHITE);
            g2.setStroke(new BasicStroke(backHovered ? 4 : 3));
            g2.drawRoundRect(wauBackButton.x, wauBackButton.y, wauBackButton.width, wauBackButton.height, 20, 20);
            g.setFont(new Font("Arial", Font.BOLD, 26));
            String backLabel = showWauDetails ? "CANCEL" : "BACK";
            FontMetrics backMetrics = g.getFontMetrics();
            int backLabelX = wauBackButton.x + (wauBackButton.width - backMetrics.stringWidth(backLabel)) / 2;
            g.drawString(backLabel, backLabelX, wauBackButton.y + 40);
            boolean selectHovered = hoveredButton == wauSelectButton;
            g.setColor(selectHovered ? new Color(90, 180, 240, 240) : new Color(65, 140, 200, 220));
            g.fillRoundRect(wauSelectButton.x, wauSelectButton.y, wauSelectButton.width, wauSelectButton.height, 20, 20);
            g.setColor(Color.WHITE);
            g2.setStroke(new BasicStroke(selectHovered ? 4 : 3));
            g2.drawRoundRect(wauSelectButton.x, wauSelectButton.y, wauSelectButton.width, wauSelectButton.height, 20, 20);
            g.setFont(new Font("Arial", Font.BOLD, 26));
            String selectLabel = "SELECT";
            FontMetrics selectMetrics = g.getFontMetrics();
            int labelX = wauSelectButton.x + (wauSelectButton.width - selectMetrics.stringWidth(selectLabel)) / 2;
            g.drawString(selectLabel, labelX, wauSelectButton.y + 40);
        } else if (!gameStarted && !readyToStart) {
            g.setColor(new Color(0, 0, 0, 100));
            g.fillRect(0, 0, WIDTH, HEIGHT);
            g.setFont(new Font("Arial", Font.BOLD, 60));
            g.setColor(new Color(0, 0, 0, 200));
            g.drawString("WAU BULAN", 132, 152);
            g.setColor(new Color(255, 215, 0));
            g.drawString("WAU BULAN", 130, 150);
            g.setFont(new Font("Arial", Font.ITALIC, 24));
            g.setColor(new Color(200, 200, 200));
            g.drawString("A Traditional Malaysian Game", 155, 190);
            g.setFont(new Font("Arial", Font.BOLD, 28));
            g.setColor(Color.WHITE);
            g.drawString("Best Score: " + highScore, 170, 230);

            boolean startHovered = hoveredButton != null && startButton.equals(hoveredButton);
            g.setColor(startHovered ? new Color(50, 180, 50, 240) : new Color(34, 139, 34, 220));
            g.fillRoundRect(startButton.x, startButton.y, startButton.width, startButton.height, 25, 25);
            g.setColor(Color.WHITE);
            g2.setStroke(new BasicStroke(startHovered ? 4 : 3));
            g2.drawRoundRect(startButton.x, startButton.y, startButton.width, startButton.height, 25, 25);
            g.setFont(new Font("Arial", Font.BOLD, 45));
            g.setColor(Color.WHITE);
            g.drawString("START GAME", 155, 410);

            boolean wauHovered = hoveredButton != null && wauButton.equals(hoveredButton);
            g.setColor(wauHovered ? new Color(100, 160, 220, 240) : new Color(70, 130, 180, 220));
            g.fillRoundRect(wauButton.x, wauButton.y, wauButton.width, wauButton.height, 25, 25);
            g.setColor(Color.WHITE);
            g2.setStroke(new BasicStroke(wauHovered ? 4 : 3));
            g2.drawRoundRect(wauButton.x, wauButton.y, wauButton.width, wauButton.height, 25, 25);
            g.setFont(new Font("Arial", Font.BOLD, 40));
            g.setColor(Color.WHITE);
            g.drawString("CHOOSE WAU", 160, 500);
            g.setFont(new Font("Arial", Font.PLAIN, 18));
            g.setColor(new Color(200, 200, 200));
            g.drawString("SPACE: Jump | P: Pause | M: Mute", 130, 740);
        }

        if (readyToStart && !gameStarted && !showWauSelection) {
            g.setFont(new Font("Arial", Font.BOLD, 40));
            g.setColor(new Color(0, 0, 0, 180));
            g.fillRoundRect(100, 320, 400, 120, 20, 20);
            g.setColor(Color.WHITE);
            g.drawString("Press SPACE", 165, 380);
            g.setFont(new Font("Arial", Font.PLAIN, 30));
            g.drawString("to start", 230, 420);
        }

        if (paused && gameStarted && !gameOver) {
            g.setFont(new Font("Arial", Font.BOLD, 50));
            g.setColor(new Color(0, 0, 0, 180));
            g.fillRoundRect(140, 330, 320, 110, 20, 20);
            g.setColor(Color.WHITE);
            g.drawString("PAUSED", 200, 400);
        }

        if (gameOver && !showWauSelection) {
            g.setColor(new Color(0, 0, 0, 150));
            g.fillRect(0, 0, WIDTH, HEIGHT);
            g.setFont(new Font("Arial", Font.BOLD, 60));
            g.setColor(new Color(255, 0, 0, 255));
            g.drawString("GAME OVER", 115, 250);
            g.setFont(new Font("Arial", Font.BOLD, 40));
            g.setColor(Color.WHITE);
            g.drawString("Score: " + score, 180, 320);
            if (score > highScore) {
                highScore = score;
                g.setColor(new Color(255, 215, 0));
                g.setFont(new Font("Arial", Font.BOLD, 32));
                g.drawString("NEW RECORD!", 165, 370);
            } else {
                g.setColor(new Color(200, 200, 200));
                g.setFont(new Font("Arial", Font.PLAIN, 28));
                g.drawString("Best: " + highScore, 200, 370);
            }
            boolean restartHovered = hoveredButton != null && restartButton.equals(hoveredButton);
            g.setColor(restartHovered ? new Color(255, 160, 20, 240) : new Color(255, 140, 0, 220));
            g.fillRoundRect(restartButton.x, restartButton.y, restartButton.width, restartButton.height, 25, 25);
            g.setColor(Color.WHITE);
            g2.setStroke(new BasicStroke(restartHovered ? 4 : 3));
            g2.drawRoundRect(restartButton.x, restartButton.y, restartButton.width, restartButton.height, 25, 25);
            g.setFont(new Font("Arial", Font.BOLD, 40));
            g.setColor(Color.WHITE);
            g.drawString("RESTART", 210, 475);

            Rectangle wauButton2 = new Rectangle(wauButton.x, wauButton.y + 70, wauButton.width, wauButton.height);
            boolean wauHovered2 = hoveredButton != null && wauButton2.equals(hoveredButton);
            g.setColor(wauHovered2 ? new Color(100, 160, 220, 240) : new Color(70, 130, 180, 220));
            g.fillRoundRect(wauButton.x, wauButton.y + 70, wauButton.width, wauButton.height, 25, 25);
            g.setColor(Color.WHITE);
            g2.setStroke(new BasicStroke(wauHovered2 ? 4 : 3));
            g2.drawRoundRect(wauButton.x, wauButton.y + 70, wauButton.width, wauButton.height, 25, 25);
            g.setFont(new Font("Arial", Font.BOLD, 35));
            g.setColor(Color.WHITE);
            g.drawString("CHOOSE WAU", 170, 570);
        }
    }

    @Override public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_SPACE) {
            if (readyToStart && !gameStarted) {
                gameStarted = true;
                if (bgMusic != null) bgMusic.loop(Clip.LOOP_CONTINUOUSLY);
                obstacles.clear();
                spawnObstacles();
            } else if (gameStarted && !gameOver) {
                velocity = JUMP_VELOCITY;
                playSound(jumpSound);
            }
        } else if (e.getKeyCode() == KeyEvent.VK_P) {
            if (gameStarted && !gameOver) {
                paused = !paused;
                if (paused) {
                    if (bgMusic != null && bgMusic.isRunning()) bgMusic.stop();
                } else {
                    if (!muted && bgMusic != null) bgMusic.loop(Clip.LOOP_CONTINUOUSLY);
                }
            }
        } else if (e.getKeyCode() == KeyEvent.VK_M) {
            muted = !muted;
            if (muted) {
                if (bgMusic != null && bgMusic.isRunning()) bgMusic.stop();
            } else {
                if (gameStarted && !gameOver && !paused && bgMusic != null) bgMusic.loop(Clip.LOOP_CONTINUOUSLY);
            }
        }
    }
    @Override public void keyReleased(KeyEvent e) {}
    @Override public void keyTyped(KeyEvent e) {}

    @Override public void mouseClicked(MouseEvent e) {}
    @Override public void mousePressed(MouseEvent e) {
        handlePointerClick(e.getX(), e.getY());
    }
    private void handlePointerClick(int mouseX, int mouseY) {
        if (showWauSelection) {
            if (wauBackButton.contains(mouseX, mouseY)) {
                if (showWauDetails) {
                    showWauDetails = false;
                    wauDetailScrollOffset = 0;
                } else {
                    showWauSelection = false;
                    syncSelectedIndexWithCurrent();
                    wauDetailScrollOffset = 0;
                }
                repaint();
                return;
            }
            if (wauSelectButton.contains(mouseX, mouseY)) {
                if (showWauDetails) {
                    currentWauType = WauType.values()[wauSelectedIndex];
                    loadImages(currentWauType);
                    syncSelectedIndexWithCurrent();
                    showWauSelection = false;
                    showWauDetails = false;
                    wauDetailScrollOffset = 0;
                } else {
                    showWauDetails = true;
                    wauDetailScrollOffset = 0;
                }
                repaint();
                return;
            }
            if (showWauDetails) {
                return;
            }
            WauType[] wauTypes = WauType.values();
            for (int i = 0; i < wauTypes.length; i++) {
                Rectangle box = wauSelectionBoxes.get(i);
                Rectangle drawBox = new Rectangle(box.x, box.y - wauScrollOffset, box.width, box.height);
                if (drawBox.contains(mouseX, mouseY)) {
                    wauSelectedIndex = i;
                    showWauDetails = false;
                    wauDetailScrollOffset = 0;
                    repaint();
                    return;
                }
            }
        } else if (!readyToStart && !gameStarted) {
            if (startButton.contains(mouseX, mouseY)) {
                readyToStart = true;
                repaint();
                return;
            }
            if (wauButton.contains(mouseX, mouseY)) {
                showWauSelection = true;
                showWauDetails = false;
                wauDetailScrollOffset = 0;
                syncSelectedIndexWithCurrent();
                repaint();
                return;
            }
        } else if (gameOver) {
            if (restartButton.contains(mouseX, mouseY)) {
                gameOver = false;
                gameStarted = false;
                readyToStart = true;
                paused = false;
                score = 0;
                y = 300;
                velocity = 0;
                obstacles.clear();
                if (bgMusic != null) {
                    bgMusic.stop();
                    bgMusic.setFramePosition(0);
                }
                repaint();
                return;
            }
            Rectangle chooseFromGameOver = new Rectangle(wauButton.x, wauButton.y + 70, wauButton.width, wauButton.height);
            if (chooseFromGameOver.contains(mouseX, mouseY)) {
                showWauSelection = true;
                showWauDetails = false;
                wauDetailScrollOffset = 0;
                syncSelectedIndexWithCurrent();
                repaint();
                return;
            }
        }
    }
    @Override public void mouseReleased(MouseEvent e) {}
    @Override public void mouseEntered(MouseEvent e) {}
    @Override public void mouseExited(MouseEvent e) {
        hoveredButton = null;
        repaint();
    }
    @Override
    public void mouseMoved(MouseEvent e) {
        int mouseX = e.getX();
        int mouseY = e.getY();
        Rectangle oldHovered = hoveredButton;
        hoveredButton = null;
        if (!gameStarted && !readyToStart) {
            if (startButton.contains(mouseX, mouseY)) {
                hoveredButton = startButton;
            } else if (wauButton.contains(mouseX, mouseY)) {
                hoveredButton = wauButton;
            }
        } else if (gameOver) {
            if (restartButton.contains(mouseX, mouseY)) {
                hoveredButton = restartButton;
            } else if (new Rectangle(wauButton.x, wauButton.y + 70, wauButton.width, wauButton.height).contains(mouseX, mouseY)) {
                hoveredButton = new Rectangle(wauButton.x, wauButton.y + 70, wauButton.width, wauButton.height);
            }
        } else if (showWauSelection) {
            Rectangle backButton = new Rectangle(200, 650, 200, 60);
            if (backButton.contains(mouseX, mouseY)) {
                hoveredButton = backButton;
            }
        }
        if ((oldHovered == null && hoveredButton != null) ||
            (oldHovered != null && hoveredButton == null) ||
            (oldHovered != hoveredButton)) {
            repaint();
        }
    }
    @Override public void mouseDragged(MouseEvent e) {}
}
