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
    private final static int POLE_WIDTH = 220;
    private final static int POLE_HEIGHT = 600;
    private final static int POLE_OVERLAP = 70;

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
    private int menuAnimationCounter = 0;
    private Rectangle hoveredButton = null;
    
    private Rectangle startButton = new Rectangle(150, 350, 300, 80);
    private Rectangle wauButton = new Rectangle(150, 450, 300, 80);
    private Rectangle restartButton = new Rectangle(150, 420, 300, 80);
    private Rectangle gameOverWauButton = new Rectangle(150, 520, 300, 80);
    
    private Rectangle wauBackButton = new Rectangle(80, 660, 180, 60);
    private Rectangle wauSelectButton = new Rectangle(340, 660, 180, 60);
    
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

    // --- ABILITY STATE VARIABLES ---
    private boolean shieldActive = false;
    private int jumpCount = 0;
    private int currentObstacleSpeed = 3;
    private int obstaclesPassed = 0; // New counter for milestone scoring

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
                        int visibleRows = 2;
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
        int startY = 360;
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
        int topY = 100; 
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
        g.drawString("History & Ability", bottomX + 24, bottomY + 40);

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
            bgImg = new ImageIcon(cl.getResource("background.png")).getImage();
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
        
        // --- MODIFIED GAP LOGIC: Added WAU_KUCING ---
        int randomGapValue = random.nextInt(81) + 40;
        if (currentWauType == WauType.WAU_KIKIK || currentWauType == WauType.WAU_PUYUH || currentWauType == WauType.WAU_KUCING) {
            randomGapValue += 50; 
        }
        
        int poleY = birdsHeight + randomGapValue;
        poles.add(new Rectangle(WIDTH, poleY, POLE_WIDTH, POLE_HEIGHT));
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (!gameStarted || gameOver || paused) return;
        menuAnimationCounter++;
        velocity += GRAVITY;
        y += velocity;

        for (int i = 0; i < birds.size(); i++) {
            birds.get(i).x -= currentObstacleSpeed;
            poles.get(i).x -= currentObstacleSpeed;
            if (birds.get(i).x + BIRD_WIDTH < x && birds.get(i).x + BIRD_WIDTH + currentObstacleSpeed >= x) {
                // Base point
                score++;
                
                // --- MODIFIED MILESTONE LOGIC ---
                if (currentWauType == WauType.WAU_BULAN || currentWauType == WauType.WAU_KENYALANG) {
                    obstaclesPassed++;
                    if (obstaclesPassed % 3 == 0) {
                        score++; // Extra mark for every 3 obstacles
                    }
                }
                
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
        boolean isSlim = (currentWauType == WauType.WAU_JALA_BUDI || currentWauType == WauType.WAU_HELANG || 
                          currentWauType == WauType.WAU_SERI_BULAN || currentWauType == WauType.WAU_SERI_NEGERI);
        
        int wauInsetX = isSlim ? 58 : 45; 
        int wauInsetY = isSlim ? 56 : 44;
        
        Rectangle wau = new Rectangle(
            x + wauInsetX, y + wauInsetY,
            WAW_WIDTH - 2 * wauInsetX, WAW_HEIGHT - 2 * wauInsetY
        );
        for (int i = 0; i < birds.size(); i++) {
            Rectangle birdObs = birds.get(i);
            Rectangle poleObs = poles.get(i);
            
            int birdInsetX = birdObs.width / 3, birdInsetY = 30;
            Rectangle birdHitbox = new Rectangle(
                birdObs.x + birdInsetX, birdObs.y + birdInsetY,
                birdObs.width - 2 * birdInsetX, birdObs.height - 2 * birdInsetY
            );
            
            int poleInsetX = poleObs.width / 3;
            Rectangle poleHitbox = new Rectangle(
                poleObs.x + poleInsetX,
                poleObs.y + POLE_TOP_CUT,
                poleObs.width - 2 * poleInsetX,
                poleObs.height - POLE_TOP_CUT - POLE_BOTTOM_CUT
            );
            
            if (wau.intersects(birdHitbox) || wau.intersects(poleHitbox)) {
                if (shieldActive) {
                    shieldActive = false;
                    birds.get(i).x = -1000; 
                    poles.get(i).x = -1000;
                    playSound(jumpSound);
                    return;
                }
                playSound(hitSound);
                gameOver = true;
                if (bgMusic != null) bgMusic.stop();
                return;
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

        if (bgImg != null) g.drawImage(bgImg, 0, 0, WIDTH, HEIGHT, null);

        for (Rectangle r : birds) g.drawImage(birdImg, r.x, r.y, r.width, r.height, null);
        for (Rectangle r : poles) g.drawImage(poleImg, r.x, r.y, POLE_WIDTH, POLE_HEIGHT, null);
        
        g.drawImage(wauImage, x, y, WAW_WIDTH, WAW_HEIGHT, null);
        g.drawImage(groundImg, 0, HEIGHT - GROUND_HEIGHT, WIDTH, GROUND_HEIGHT, null);

        if (shieldActive) {
            g.setColor(new Color(255, 255, 255, 100));
            g.fillOval(x + 20, y + 20, WAW_WIDTH - 40, WAW_HEIGHT - 40);
        }

        g.setFont(new Font("Arial", Font.BOLD, 30));
        g.setColor(Color.BLACK);
        g.drawString("Score: " + score, 22, 42);
        g.setColor(Color.WHITE);
        g.drawString("Score: " + score, 20, 40);
        
        if (showWauSelection) {
            g.setColor(new Color(0, 0, 0, 220));
            g.fillRect(0, 0, WIDTH, HEIGHT);
            WauType[] wauTypes = WauType.values();
            WauType selectedWau = wauTypes[wauSelectedIndex];
            Graphics2D g2 = (Graphics2D) g;

            if (showWauDetails) {
                drawWauDetailsPage(g2, g, selectedWau);
            } else {
                g.setFont(new Font("Arial", Font.BOLD, 38));
                g.setColor(new Color(255, 215, 0));
                g.drawString("SELECT YOUR WAU", 110, 35);

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
                    g.drawImage(wauImg, previewBoxX + 18, previewBoxY + 18, previewBoxSize - 36, previewBoxSize - 36, null);
                } catch (Exception e) {}

                g.setFont(new Font("Arial", Font.BOLD, 28));
                g.setColor(Color.WHITE);
                String wauName = selectedWau.getDisplayName();
                g.drawString(wauName, previewBoxX + (previewBoxSize - g.getFontMetrics().stringWidth(wauName)) / 2, previewBoxY + previewBoxSize + 48);

                int gridTop = previewBoxY + previewBoxSize + 80;
                Shape oldClip = g.getClip();
                g.setClip(40, gridTop, WIDTH - 80, 320);
                for (int i = 0; i < wauTypes.length; i++) {
                    Rectangle box = wauSelectionBoxes.get(i);
                    int drawY = box.y - wauScrollOffset;
                    if (drawY + box.height < gridTop || drawY > gridTop + 320) continue;
                    
                    if (i == wauSelectedIndex) {
                        g.setColor(new Color(255, 215, 0, 100));
                        g.fillRoundRect(box.x - 8, drawY - 8, box.width + 16, box.height + 16, 20, 20);
                    }
                    g.setColor(Color.WHITE);
                    g2.setStroke(new BasicStroke(3));
                    g2.drawRoundRect(box.x, drawY, box.width, box.height, 10, 10);
                    try {
                        Image wauImg = new ImageIcon(getClass().getClassLoader().getResource(wauTypes[i].image)).getImage();
                        g.drawImage(wauImg, box.x + 10, drawY + 10, box.width - 20, box.height - 20, null);
                    } catch (Exception e) {}
                }
                g.setClip(oldClip);
            }

            g.setFont(new Font("Arial", Font.BOLD, 28));
            
            boolean backHovered = hoveredButton == wauBackButton;
            g.setColor(backHovered ? new Color(220, 80, 80, 240) : new Color(200, 50, 50, 220));
            g.fillRoundRect(wauBackButton.x, wauBackButton.y, wauBackButton.width, wauBackButton.height, 20, 20);
            g.setColor(Color.WHITE);
            g.drawString(showWauDetails ? "CANCEL" : "BACK", wauBackButton.x + (wauBackButton.width - g.getFontMetrics().stringWidth(showWauDetails ? "CANCEL" : "BACK"))/2, wauBackButton.y + 40);

            boolean selectHovered = hoveredButton == wauSelectButton;
            g.setColor(selectHovered ? new Color(90, 180, 240, 240) : new Color(65, 140, 200, 220));
            g.fillRoundRect(wauSelectButton.x, wauSelectButton.y, wauSelectButton.width, wauSelectButton.height, 20, 20);
            g.setColor(Color.WHITE);
            g.drawString("SELECT", wauSelectButton.x + (wauSelectButton.width - g.getFontMetrics().stringWidth("SELECT"))/2, wauSelectButton.y + 40);

        } else if (!gameStarted && !readyToStart) {
            g.setColor(new Color(0, 0, 0, 100));
            g.fillRect(0, 0, WIDTH, HEIGHT);
            g.setFont(new Font("Arial", Font.BOLD, 60));
            g.setColor(Color.BLACK);
            g.drawString("WAU BULAN", 132, 152);
            
            g.setColor(new Color(34, 139, 34, 220));
            g.fillRoundRect(startButton.x, startButton.y, startButton.width, startButton.height, 25, 25);
            g.setColor(Color.WHITE);
            g.setFont(new Font("Arial", Font.BOLD, 45));
            g.drawString("START GAME", 155, 410);
            
            g.setColor(new Color(70, 130, 180, 220));
            g.fillRoundRect(wauButton.x, wauButton.y, wauButton.width, wauButton.height, 25, 25);
            g.setColor(Color.WHITE);
            g.setFont(new Font("Arial", Font.BOLD, 40));
            g.drawString("CHOOSE WAU", 160, 500);
        } else if (readyToStart && !gameStarted) {
            g.setFont(new Font("Arial", Font.BOLD, 35));
            g.setColor(new Color(0, 0, 0, 180));
            g.fillRoundRect(100, 320, 400, 120, 20, 20);
            g.setColor(Color.WHITE);
            g.drawString("Press SPACE to START", 105, 395);
        } else if (paused) {
            g.setFont(new Font("Arial", Font.BOLD, 50));
            g.drawString("PAUSED", 200, 400);
        } else if (gameOver) {
            g.setColor(new Color(0, 0, 0, 150));
            g.fillRect(0, 0, WIDTH, HEIGHT);
            
            g.setFont(new Font("Arial", Font.BOLD, 60));
            g.setColor(Color.RED);
            g.drawString("GAME OVER", 115, 250);
            
            g.setFont(new Font("Arial", Font.BOLD, 40));
            g.setColor(Color.WHITE);
            String scoreText = "Score: " + score;
            int scoreWidth = g.getFontMetrics().stringWidth(scoreText);
            g.drawString(scoreText, (WIDTH - scoreWidth) / 2, 350);

            g.setColor(new Color(255, 140, 0, 220));
            g.fillRoundRect(restartButton.x, restartButton.y, restartButton.width, restartButton.height, 25, 25);
            g.setColor(Color.WHITE);
            g.setFont(new Font("Arial", Font.BOLD, 40));
            g.drawString("RESTART", 210, 475);
            
            g.setColor(new Color(70, 130, 180, 220));
            g.fillRoundRect(gameOverWauButton.x, gameOverWauButton.y, gameOverWauButton.width, gameOverWauButton.height, 25, 25);
            g.setColor(Color.WHITE);
            g.setFont(new Font("Arial", Font.BOLD, 40));
            g.drawString("CHOOSE WAU", 160, 575);
        }
    }

    @Override public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_SPACE) {
            if (readyToStart && !gameStarted) {
                gameStarted = true;
                jumpCount = 1;
                obstaclesPassed = 0; // Reset milestone counter
                shieldActive = (currentWauType == WauType.WAU_DODO_HELANG || currentWauType == WauType.WAU_BARAT || currentWauType == WauType.WAU_KEBAYAK);
                currentObstacleSpeed = (currentWauType == WauType.WAU_MERAK || currentWauType == WauType.WAU_KAPAL || currentWauType == WauType.WAU_KANGKANG) ? 2 : 3;
                
                if (bgMusic != null) bgMusic.loop(Clip.LOOP_CONTINUOUSLY);
                spawnObstacles();
            } else if (gameStarted && !gameOver) {
                // --- MODIFIED DOUBLE JUMP LOGIC: Kucing removed ---
                boolean canDouble = false; 
                if (jumpCount < (canDouble ? 2 : 1)) {
                    velocity = JUMP_VELOCITY;
                    jumpCount++;
                    playSound(jumpSound);
                } else if (jumpCount == 1 && !canDouble) {
                    velocity = JUMP_VELOCITY;
                    playSound(jumpSound);
                }
            }
        } else if (e.getKeyCode() == KeyEvent.VK_P) {
            paused = !paused;
        } else if (e.getKeyCode() == KeyEvent.VK_M) {
            muted = !muted;
        }
    }
    @Override public void keyReleased(KeyEvent e) {}
    @Override public void keyTyped(KeyEvent e) {}
    
    private void handlePointerClick(int mouseX, int mouseY) {
        if (showWauSelection) {
            if (wauBackButton.contains(mouseX, mouseY)) {
                if (showWauDetails) showWauDetails = false;
                else showWauSelection = false;
                repaint();
                return;
            }
            if (wauSelectButton.contains(mouseX, mouseY)) {
                if (showWauDetails) {
                    currentWauType = WauType.values()[wauSelectedIndex];
                    loadImages(currentWauType);
                    showWauSelection = false;
                    showWauDetails = false;
                } else {
                    showWauDetails = true;
                }
                repaint();
                return;
            }
            if (!showWauDetails) {
                for (int i = 0; i < WauType.values().length; i++) {
                    Rectangle box = wauSelectionBoxes.get(i);
                    if (new Rectangle(box.x, box.y - wauScrollOffset, box.width, box.height).contains(mouseX, mouseY)) {
                        wauSelectedIndex = i;
                        repaint();
                    }
                }
            }
        } else if (!readyToStart && !gameStarted) {
            if (startButton.contains(mouseX, mouseY)) readyToStart = true;
            if (wauButton.contains(mouseX, mouseY)) {
                showWauSelection = true;
                syncSelectedIndexWithCurrent();
            }
            repaint();
        } else if (gameOver) {
            if (restartButton.contains(mouseX, mouseY)) {
                gameOver = false; gameStarted = false; readyToStart = true; score = 0; y = 300; velocity = 0;
                obstaclesPassed = 0; // Reset milestone counter
                birds.clear(); poles.clear();
            }
            if (gameOverWauButton.contains(mouseX, mouseY)) {
                showWauSelection = true;
                syncSelectedIndexWithCurrent();
            }
            repaint();
        }
    }

    @Override public void mousePressed(MouseEvent e) { handlePointerClick(e.getX(), e.getY()); }
    @Override public void mouseMoved(MouseEvent e) { hoveredButton = new Rectangle(e.getX(), e.getY(), 1, 1); repaint(); }
    @Override public void mouseClicked(MouseEvent e) {}
    @Override public void mouseReleased(MouseEvent e) {}
    @Override public void mouseEntered(MouseEvent e) {}
    @Override public void mouseExited(MouseEvent e) {}
    @Override public void mouseDragged(MouseEvent e) {}
}