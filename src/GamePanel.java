import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Random;
import javax.sound.sampled.*;

public class GamePanel extends JPanel implements ActionListener, KeyListener, MouseListener, MouseMotionListener {
    private final static int WIDTH = 600;
    private final static int HEIGHT = 800;
    private final static int GROUND_HEIGHT = 54; // Set to your ground.png height!
    private final static int POLE_WIDTH = 220;   // Very wide pipe effect!
    private final static int POLE_HEIGHT = 600;  // Tall!
    private final static int POLE_OVERLAP = 70;  // Amount pole sinks into ground for perfect "rooted" appearance

    private final static double GRAVITY = 0.15;
    private final static double JUMP_VELOCITY = -4;
    private final static int OBSTACLE_SPEED = 3;
    private final static int BIRD_WIDTH = 240;
    private final static int MIN_OBSTACLE_HEIGHT = 200, MAX_OBSTACLE_HEIGHT = 350;
    private final static int GAP = 20;
    private final static int WAW_WIDTH = 130, WAW_HEIGHT = 130;

    private int x = 100, y = 300;
    private double velocity = 0;
    private int bgOffset = 0;
    private int groundOffset = 0;
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
    private ArrayList<Rectangle> birds = new ArrayList<>();
    private ArrayList<Rectangle> poles = new ArrayList<>();
    private Clip jumpSound, hitSound, scoreSound, bgMusic;
    private int menuAnimationCounter = 0; // For button animations
    private Rectangle hoveredButton = null; // Track hovered button
    
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

    // --- HITBOX TUNING VARIABLES ---
    // Adjust this to cut more or less transparency from the top of the pole image
    private final int POLE_TOP_CUT = 100; 
    private final int POLE_BOTTOM_CUT = 30;

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

    void loadImages(WauType type) {
        try {
            ClassLoader cl = getClass().getClassLoader();
            wauImage = new ImageIcon(cl.getResource(type.image)).getImage();
            try {
                bgImg = new ImageIcon(cl.getResource("background.png")).getImage();
            } catch (Exception ex) {
                System.err.println("background.png not found");
                bgImg = new ImageIcon(cl.getResource("background.png")).getImage();
            }
            groundImg = new ImageIcon(cl.getResource("ground.png")).getImage();
            birdImg = new ImageIcon(cl.getResource("birds.png")).getImage();
            poleImg = new ImageIcon(cl.getResource("coconut.png")).getImage();
        } catch (Exception e) {
            System.err.println("Image loading failed: " + e.getMessage());
        }
    }

    Image loadImage(String name) {
        try {
            ClassLoader cl = getClass().getClassLoader();
            java.net.URL url = cl.getResource(name);
            if (url != null) {
                return new ImageIcon(url).getImage();
            }
            java.io.File f = new java.io.File(name);
            if (f.exists()) {
                return new ImageIcon(f.getAbsolutePath()).getImage();
            }
        } catch (Exception ignore) {}
        System.err.println("Failed to load image: " + name);
        return null;
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
        int birdsHeight = random.nextInt(MAX_OBSTACLE_HEIGHT - MIN_OBSTACLE_HEIGHT + 1) + MIN_OBSTACLE_HEIGHT;
        birds.add(new Rectangle(WIDTH, 0, BIRD_WIDTH, birdsHeight));
        // Random gap between 40 and 120 pixels
        int randomGap = random.nextInt(81) + 40; // 40 to 120
        int poleY = birdsHeight + randomGap;
        poles.add(new Rectangle(WIDTH, poleY, POLE_WIDTH, POLE_HEIGHT));
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (!gameStarted || gameOver || paused) return;
        menuAnimationCounter++;
        velocity += GRAVITY;
        y += velocity;

        for (int i = 0; i < birds.size(); i++) {
            birds.get(i).x -= OBSTACLE_SPEED;
            poles.get(i).x -= OBSTACLE_SPEED;
            if (birds.get(i).x + BIRD_WIDTH < x && birds.get(i).x + BIRD_WIDTH + OBSTACLE_SPEED >= x) {
                score++;
                playSound(scoreSound);
                if (!muted) bgMusic.start();
                try { Thread.sleep(80); } catch (InterruptedException ex) {}
            }
        }
        if (!birds.isEmpty() && birds.get(0).x < -BIRD_WIDTH) {
            birds.remove(0);
            poles.remove(0);
            spawnObstacles();
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
        for (int i = 0; i < birds.size(); i++) {
            Rectangle birdObs = birds.get(i);
            Rectangle poleObs = poles.get(i);
            
            // Bird Hitbox
            int birdInsetX = birdObs.width / 3, birdInsetY = 30;
            Rectangle birdHitbox = new Rectangle(
                birdObs.x + birdInsetX, birdObs.y + birdInsetY,
                birdObs.width - 2 * birdInsetX, birdObs.height - 2 * birdInsetY
            );
            
            // Pole Hitbox (Adjusted for transparency)
            int poleInsetX = poleObs.width / 3;
            
            Rectangle poleHitbox = new Rectangle(
                poleObs.x + poleInsetX,
                poleObs.y + POLE_TOP_CUT, // Start hitbox lower to skip sky transparency
                poleObs.width - 2 * poleInsetX,
                poleObs.height - POLE_TOP_CUT - POLE_BOTTOM_CUT // Shrink height from both ends
            );
            
            if (wau.intersects(birdHitbox) || wau.intersects(poleHitbox)) {
                playSound(hitSound);
                gameOver = true;
                if (bgMusic != null) bgMusic.stop();
                return;
            }
        }
        // "Death" at top or bottom boundaries
        if (y < 0 || y + WAW_HEIGHT > HEIGHT - GROUND_HEIGHT) {
            playSound(hitSound);
            gameOver = true;
            if (bgMusic != null) bgMusic.stop();
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        // Ensure a solid base if image missing
        g.setColor(new Color(135, 206, 235)); // sky blue fallback
        g.fillRect(0, 0, WIDTH, HEIGHT);

        // Draw static background
        if (bgImg != null) {
            g.drawImage(bgImg, 0, 0, WIDTH, HEIGHT, null);
        }

        for (Rectangle r : birds) {
            if (birdImg != null) {
                g.drawImage(birdImg, r.x, r.y, r.width, r.height, null);
            } else {
                g.setColor(Color.RED);
                g.fillRect(r.x, r.y, r.width, r.height);
            }
        }
        for (Rectangle r : poles) {
            if (poleImg != null) {
                g.drawImage(poleImg, r.x, r.y, POLE_WIDTH, POLE_HEIGHT, null);
            } else {
                g.setColor(Color.GREEN);
                g.fillRect(r.x, r.y, POLE_WIDTH, POLE_HEIGHT);
            }
        }
        g.drawImage(wauImage, x, y, WAW_WIDTH, WAW_HEIGHT, null);

        int groundY = HEIGHT - GROUND_HEIGHT;
        // Draw ground
        g.drawImage(groundImg, 0, groundY, WIDTH, GROUND_HEIGHT, null);

        // DEBUG: Hitboxes
        Graphics2D g2 = (Graphics2D) g;
        g2.setColor(Color.GREEN);
        g2.drawRect(x + 45, y + 44, WAW_WIDTH - 90, WAW_HEIGHT - 88);
        
        for (int i = 0; i < birds.size(); i++) {
            Rectangle birdObs = birds.get(i);
            Rectangle poleObs = poles.get(i);
            
            // Draw Bird Hitbox
            int birdInsetX = birdObs.width / 3, birdInsetY = 30;
            g2.setColor(Color.MAGENTA);
            g2.drawRect(
                birdObs.x + birdInsetX, birdObs.y + birdInsetY,
                birdObs.width - 2 * birdInsetX, birdObs.height - 2 * birdInsetY
            );
            
            // Draw Pole Hitbox (Matching checkCollision logic)
            int poleInsetX = poleObs.width / 3;
            g2.drawRect(
                poleObs.x + poleInsetX,
                poleObs.y + POLE_TOP_CUT,
                poleObs.width - 2 * poleInsetX,
                poleObs.height - POLE_TOP_CUT - POLE_BOTTOM_CUT
            );
        }
        
        g.setFont(new Font("Arial", Font.BOLD, 30));
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
            // Enhanced Main Menu Screen
            g.setColor(new Color(0, 0, 0, 100));
            g.fillRect(0, 0, WIDTH, HEIGHT);
            
            // Title
            g.setFont(new Font("Arial", Font.BOLD, 60));
            g.setColor(new Color(0, 0, 0, 200));
            g.drawString("WAU BULAN", 132, 152);
            // Subtitle
            g.setFont(new Font("Arial", Font.ITALIC, 24));
            g.setColor(new Color(200, 200, 200));
            g.drawString("A Traditional Malaysian Game", 155, 190);
            
            // Score display
            g.setFont(new Font("Arial", Font.BOLD, 28));
            g.setColor(Color.WHITE);
            g.drawString("Best Score: " + highScore, 170, 230);
            
            // Start Button with hover effect
            boolean startHovered = hoveredButton != null && startButton.contains(hoveredButton);
            g.setColor(startHovered ? new Color(50, 180, 50, 240) : new Color(34, 139, 34, 220));
            g.fillRoundRect(startButton.x, startButton.y, startButton.width, startButton.height, 25, 25);
            g.setColor(Color.WHITE);
            g2.setStroke(new BasicStroke(startHovered ? 4 : 3));
            g2.drawRoundRect(startButton.x, startButton.y, startButton.width, startButton.height, 25, 25);
            
            g.setFont(new Font("Arial", Font.BOLD, 45));
            g.setColor(Color.WHITE);
            g.drawString("START GAME", 155, 410);
            
            // Choose Wau Button with hover effect
            boolean wauHovered = hoveredButton != null && wauButton.contains(hoveredButton);
            g.setColor(wauHovered ? new Color(100, 160, 220, 240) : new Color(70, 130, 180, 220));
            g.fillRoundRect(wauButton.x, wauButton.y, wauButton.width, wauButton.height, 25, 25);
            g.setColor(Color.WHITE);
            g2.setStroke(new BasicStroke(wauHovered ? 4 : 3));
            g2.drawRoundRect(wauButton.x, wauButton.y, wauButton.width, wauButton.height, 25, 25);
            
            g.setFont(new Font("Arial", Font.BOLD, 40));
            g.setColor(Color.WHITE);
            g.drawString("CHOOSE WAU", 160, 500);
            
            // Instructions
            g.setFont(new Font("Arial", Font.PLAIN, 18));
            g.setColor(new Color(200, 200, 200));
            g.drawString("SPACE: Jump | P: Pause | M: Mute", 130, 740);
        }
        
        if (readyToStart && !gameStarted && !showWauSelection) {
            // Show "Press SPACE to start" message
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
            // Game Over Screen with semi-transparent overlay
            g.setColor(new Color(0, 0, 0, 150));
            g.fillRect(0, 0, WIDTH, HEIGHT);
            
            g.setFont(new Font("Arial", Font.BOLD, 60));
            g.setColor(new Color(255, 0, 0, 255));
            g.drawString("GAME OVER", 115, 250);
            
            // Score display
            g.setFont(new Font("Arial", Font.BOLD, 40));
            g.setColor(Color.WHITE);
            g.drawString("Score: " + score, 180, 320);
            
            // High score update
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
            
            // Restart Button with hover effect
            boolean restartHovered = hoveredButton != null && restartButton.contains(hoveredButton);
            g.setColor(restartHovered ? new Color(255, 160, 20, 240) : new Color(255, 140, 0, 220));
            g.fillRoundRect(restartButton.x, restartButton.y, restartButton.width, restartButton.height, 25, 25);
            g.setColor(Color.WHITE);
            g2.setStroke(new BasicStroke(restartHovered ? 4 : 3));
            g2.drawRoundRect(restartButton.x, restartButton.y, restartButton.width, restartButton.height, 25, 25);
            
            g.setFont(new Font("Arial", Font.BOLD, 40));
            g.setColor(Color.WHITE);
            g.drawString("RESTART", 210, 475);
            
            // Choose Wau Button
            Rectangle wauButton2 = new Rectangle(wauButton.x, wauButton.y + 70, wauButton.width, wauButton.height);
            boolean wauHovered2 = hoveredButton != null && wauButton2.contains(hoveredButton);
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
                // Start the game after clicking start button
                gameStarted = true;
                if (bgMusic != null) bgMusic.loop(Clip.LOOP_CONTINUOUSLY);
                spawnObstacles();
            } else if (gameStarted && !gameOver) {
                // Jump during gameplay
                velocity = JUMP_VELOCITY;
                playSound(jumpSound);
                try { Thread.sleep(80); } catch (InterruptedException ex) {}
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
                bgOffset = 0;
                groundOffset = 0;
                y = 300;
                velocity = 0;
                birds.clear();
                poles.clear();
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

    @Override public void mouseClicked(MouseEvent e) {}
    @Override public void mousePressed(MouseEvent e) {
        handlePointerClick(e.getX(), e.getY());
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
        
        // Check which button is being hovered over
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
            if (wauBackButton.contains(mouseX, mouseY)) {
                hoveredButton = wauBackButton;
            } else if (wauSelectButton.contains(mouseX, mouseY)) {
                hoveredButton = wauSelectButton;
            }
        }
        
        if ((oldHovered == null && hoveredButton != null) || 
            (oldHovered != null && hoveredButton == null) ||
            (oldHovered != hoveredButton)) {
            repaint();
        }
    }
    
    @Override
    public void mouseDragged(MouseEvent e) {}
}
