import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Random;
import javax.sound.sampled.*;

public class GamePanel extends JPanel implements ActionListener, KeyListener, MouseListener, MouseMotionListener {
    // --- CONSTANTS ---
    private final static int WIDTH = 600;
    private final static int HEIGHT = 800;
    private final static int GROUND_HEIGHT = 54;
    private final static int POLE_WIDTH = 220;
    private final static int POLE_HEIGHT = 600;
    private final static int BIRD_WIDTH = 240;
    private final static int BIRD_ONLY_HEIGHT = 240;
    private final static int BIRD_PAIR_HEIGHT = 240;
    private final static int WAW_WIDTH = 130, WAW_HEIGHT = 130;

    private final static double GRAVITY = 0.15;
    private final static double JUMP_VELOCITY = -4;
    private final static int BASE_OBSTACLE_SPEED = 3;
    private final static int MAX_OBSTACLE_SPEED = 12;

    // --- GAME STATE ---
    private int x = 100, y = 300;
    private double velocity = 0;
    private int score = 0;
    private int highScore = 0;
    private Timer timer;
    private final Random random = new Random();
    private boolean gameOver = false, gameStarted = false, readyToStart = false, paused = false, muted = false;
    private boolean showNewRecordText = false; 

    // --- ASSETS ---
    private Image wauImage, bgImg, birdImg, poleImg, groundImg;
    private Clip jumpSound, hitSound, scoreSound, bgMusic;

    // --- FRIEND'S MECHANICS: OBSTACLES & ANIMATION ---
    private int scoreBumpTimer = 0;
    private static final int SCORE_BUMP_DURATION = 10;

    enum ObstacleType { BIRD_ONLY, POLE_ONLY, BOTH }
    class ObstaclePair {
        Rectangle birdRect;
        Rectangle poleRect;
        boolean scored = false; 
        public ObstaclePair(Rectangle bird, Rectangle pole) {
            this.birdRect = bird;
            this.poleRect = pole;
        }
    }
    private ArrayList<ObstaclePair> obstacles = new ArrayList<>();

    // --- USER'S MECHANICS: WAU SELECTION & ABILITIES ---
    private WauType currentWauType;
    private boolean showWauSelection = false, showWauDetails = false;
    private int wauSelectedIndex = 0, wauScrollOffset = 0, wauMaxScroll = 0;
    private int wauDetailScrollOffset = 0, wauDetailScrollMax = 0;
    private final int WAU_BOX_SIZE = 120, WAU_BOX_GAP = 30;
    private ArrayList<Rectangle> wauSelectionBoxes = new ArrayList<>();
    
    private boolean shieldActive = false;
    private int jumpCount = 0;
    private int currentObstacleSpeed = 3;
    private int obstaclesPassed = 0; 

    // UI Buttons
    private Rectangle startButton = new Rectangle(150, 350, 300, 80);
    private Rectangle wauButton = new Rectangle(150, 450, 300, 80);
    private Rectangle restartButton = new Rectangle(150, 420, 300, 80);
    private Rectangle gameOverWauButton = new Rectangle(150, 520, 300, 80);
    private Rectangle wauBackButton = new Rectangle(80, 660, 180, 60);
    private Rectangle wauSelectButton = new Rectangle(340, 660, 180, 60);
    private Rectangle hoveredButton = null;

    public GamePanel(WauType type) {
        this.setPreferredSize(new Dimension(WIDTH, HEIGHT));
        this.setFocusable(true);
        this.addKeyListener(this);
        this.addMouseListener(this);
        this.addMouseMotionListener(this);
        
        this.addMouseWheelListener(e -> {
            if (showWauSelection) {
                if (showWauDetails) {
                    wauDetailScrollOffset = Math.max(0, Math.min(wauDetailScrollMax, wauDetailScrollOffset + e.getWheelRotation() * 30));
                } else {
                    int rows = (int)Math.ceil(WauType.values().length / 3.0);
                    wauMaxScroll = Math.max(0, (rows - 2) * (WAU_BOX_SIZE + WAU_BOX_GAP + 25));
                    wauScrollOffset = Math.max(0, Math.min(wauMaxScroll, wauScrollOffset + e.getWheelRotation() * 40));
                }
                repaint();
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

    // --- GAME LOGIC ---

    void spawnObstacles() {
        int rand = random.nextInt(3);
        ObstacleType type = ObstacleType.values()[rand];

        if (type == ObstacleType.BIRD_ONLY) {
            int yBird = random.nextInt(HEIGHT - GROUND_HEIGHT - BIRD_ONLY_HEIGHT - 100) + 50;
            obstacles.add(new ObstaclePair(new Rectangle(WIDTH, yBird, BIRD_WIDTH, BIRD_ONLY_HEIGHT), null));
        } else if (type == ObstacleType.POLE_ONLY) {
            int poleY = HEIGHT - GROUND_HEIGHT - POLE_HEIGHT + 70;
            obstacles.add(new ObstaclePair(null, new Rectangle(WIDTH, poleY, POLE_WIDTH, POLE_HEIGHT)));
        } else {
            int gap = random.nextInt(81) + 40;
            if (currentWauType == WauType.WAU_KIKIK || currentWauType == WauType.WAU_PUYUH || currentWauType == WauType.WAU_KUCING) gap += 50;
            obstacles.add(new ObstaclePair(
                new Rectangle(WIDTH, 0, BIRD_WIDTH, BIRD_PAIR_HEIGHT),
                new Rectangle(WIDTH, BIRD_PAIR_HEIGHT + gap, POLE_WIDTH, POLE_HEIGHT)
            ));
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (!gameStarted || gameOver || paused) return;
        velocity += GRAVITY;
        y += velocity;

        int speed = Math.min(currentObstacleSpeed + score / 5, MAX_OBSTACLE_SPEED);

        for (int i = 0; i < obstacles.size(); i++) {
            ObstaclePair op = obstacles.get(i);
            if (op.birdRect != null) op.birdRect.x -= speed;
            if (op.poleRect != null) op.poleRect.x -= speed;

            Rectangle ref = (op.birdRect != null) ? op.birdRect : op.poleRect;
            if (!op.scored && ref.x + ref.width < x) {
                score++;
                op.scored = true;
                scoreBumpTimer = SCORE_BUMP_DURATION;
                if (currentWauType == WauType.WAU_BULAN || currentWauType == WauType.WAU_KENYALANG) {
                    obstaclesPassed++;
                    if (obstaclesPassed % 3 == 0) score++;
                }
                playSound(scoreSound);
                if (!muted && bgMusic != null && !bgMusic.isRunning()) bgMusic.start();
            }
        }

        if (!obstacles.isEmpty()) {
            ObstaclePair first = obstacles.get(0);
            Rectangle ref = (first.birdRect != null) ? first.birdRect : first.poleRect;
            if (ref.x < -BIRD_WIDTH) { obstacles.remove(0); spawnObstacles(); }
        }
        checkCollision();
        repaint();
    }

    void checkCollision() {
        boolean isSlim = (currentWauType == WauType.WAU_JALA_BUDI || currentWauType == WauType.WAU_HELANG || 
                          currentWauType == WauType.WAU_SERI_BULAN || currentWauType == WauType.WAU_SERI_NEGERI);
        int wauInsetX = isSlim ? 58 : 45, wauInsetY = isSlim ? 56 : 44;
        Rectangle wau = new Rectangle(x + wauInsetX, y + wauInsetY, WAW_WIDTH - 2 * wauInsetX, WAW_HEIGHT - 2 * wauInsetY);

        for (int i = 0; i < obstacles.size(); i++) {
            ObstaclePair op = obstacles.get(i);
            if (collides(wau, op.birdRect, true) || collides(wau, op.poleRect, false)) {
                if (shieldActive) {
                    shieldActive = false; obstacles.remove(i); spawnObstacles(); playSound(jumpSound); return;
                }
                endGame(); return;
            }
        }
        if (y < 0 || y + WAW_HEIGHT > HEIGHT - GROUND_HEIGHT) endGame();
    }

    private boolean collides(Rectangle wau, Rectangle obs, boolean isBird) {
        if (obs == null) return false;
        int iX = obs.width / 3, iY = isBird ? 30 : 100;
        Rectangle hitbox = new Rectangle(obs.x + iX, obs.y + (isBird ? iY : 100), obs.width - 2 * iX, obs.height - (isBird ? 2 * iY : 130));
        return wau.intersects(hitbox);
    }

    private void endGame() {
        obstaclesPassed=0;
        playSound(hitSound); gameOver = true;
        if (bgMusic != null) bgMusic.stop();
        if (score > highScore) {
            highScore = score;
            showNewRecordText = true; 
        } else {
            showNewRecordText = false;
        }
    }

    // --- PAINTING ---

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        if (bgImg != null) g.drawImage(bgImg, 0, 0, WIDTH, HEIGHT, null);

        for (ObstaclePair op : obstacles) {
            if (op.birdRect != null) g.drawImage(birdImg, op.birdRect.x, op.birdRect.y, op.birdRect.width, op.birdRect.height, null);
            if (op.poleRect != null) g.drawImage(poleImg, op.poleRect.x, op.poleRect.y, POLE_WIDTH, POLE_HEIGHT, null);
        }

        double angle = Math.toRadians(Math.max(-15, Math.min(45, velocity * 8)));
        g2.rotate(angle, x + WAW_WIDTH / 2, y + WAW_HEIGHT / 2);
        g.drawImage(wauImage, x, y, WAW_WIDTH, WAW_HEIGHT, null);
        if (shieldActive) {
            g.setColor(new Color(255, 255, 255, 100));
            g.fillOval(x + 20, y + 20, WAW_WIDTH - 40, WAW_HEIGHT - 40);
        }
        g2.rotate(-angle, x + WAW_WIDTH / 2, y + WAW_HEIGHT / 2); 

        g.drawImage(groundImg, 0, HEIGHT - GROUND_HEIGHT, WIDTH, GROUND_HEIGHT, null);
        drawUI(g, g2);
    }

    private void drawUI(Graphics g, Graphics2D g2) {
        int fontSize = (scoreBumpTimer > 0) ? 42 : 30;
        if (scoreBumpTimer > 0) scoreBumpTimer--;
        g.setFont(new Font("Arial", Font.BOLD, fontSize));
        g.setColor(Color.BLACK); g.drawString("Score: " + score, 22, 42);
        g.setColor(Color.WHITE); g.drawString("Score: " + score, 20, 40);

        if (showWauSelection) drawSelectionOverlay(g, g2);
        else if (!gameStarted && !readyToStart) drawMainMenu(g, g2);
        else if (readyToStart && !gameStarted) drawReadyPrompt(g);
        else if (paused) { g.setFont(new Font("Arial", Font.BOLD, 50)); g.drawString("PAUSED", 200, 400); }
        else if (gameOver) drawGameOverMenu(g, g2);
    }

    private void drawWauDetailsPage(Graphics2D g2, Graphics g, WauType selectedWau) {
        int topX = 60, topY = 100, topW = WIDTH - 120, topH = 220;
        
        g.setColor(new Color(0, 0, 0, 180)); 
        g.fillRoundRect(topX, topY, topW, topH, 28, 28);
        g2.setColor(new Color(255, 215, 0));
        g2.setStroke(new BasicStroke(4));
        g2.drawRoundRect(topX, topY, topW, topH, 28, 28);
        
        try {
            Image img = new ImageIcon(getClass().getClassLoader().getResource(selectedWau.image)).getImage();
            g.drawImage(img, topX + (topW - 160) / 2, topY + 20, 160, 160, null);
        } catch (Exception e) {}

        g.setFont(new Font("Arial", Font.BOLD, 32));
        g.setColor(Color.WHITE);
        String name = selectedWau.getDisplayName();
        g.drawString(name, topX + (topW - g.getFontMetrics().stringWidth(name)) / 2, topY + topH - 25);

        int bY = topY + topH + 30, bH = 300;
        g.setColor(new Color(0, 0, 0, 200)); 
        g.fillRoundRect(topX, bY, topW, bH, 28, 28);
        g2.setColor(Color.WHITE);
        g2.setStroke(new BasicStroke(2));
        g2.drawRoundRect(topX, bY, topW, bH, 28, 28);

        g.setFont(new Font("Arial", Font.BOLD, 26)); 
        g.setColor(new Color(255, 215, 0)); 
        g.drawString("History & Ability", topX + 24, bY + 45);

        g.setFont(new Font("Arial", Font.BOLD, 17)); 
        FontMetrics fm = g.getFontMetrics();
        wauDetailScrollMax = Math.max(0, computeWrappedHeight(selectedWau.getDetails(), fm, topW - 48, 24) - (bH - 80));
        
        Shape oldClip = g.getClip();
        g.setClip(topX + 18, bY + 60, topW - 36, bH - 80);
        drawWrappedString(g, selectedWau.getDetails(), topX + 24, bY + 85 - wauDetailScrollOffset, topW - 48, 24);
        g.setClip(oldClip);
    }

    private int drawWrappedString(Graphics g, String text, int x, int y, int maxWidth, int lineHeight) {
        int curY = y;
        for (String raw : text.split("\\n")) {
            if (raw.trim().isEmpty()) { curY += lineHeight; continue; }
            StringBuilder sb = new StringBuilder();
            for (String word : raw.split(" ")) {
                if (g.getFontMetrics().stringWidth(sb.toString() + word) > maxWidth) {
                    g.setColor(Color.BLACK); g.drawString(sb.toString(), x + 1, curY + 1);
                    g.setColor(Color.WHITE); g.drawString(sb.toString(), x, curY);
                    curY += lineHeight; sb.setLength(0);
                }
                sb.append(word).append(" ");
            }
            g.setColor(Color.BLACK); g.drawString(sb.toString(), x + 1, curY + 1);
            g.setColor(Color.WHITE); g.drawString(sb.toString(), x, curY);
            curY += lineHeight;
        }
        return curY;
    }

    private int computeWrappedHeight(String text, FontMetrics fm, int maxWidth, int lineHeight) {
        int h = 0;
        for (String raw : text.split("\\n")) {
            if (raw.trim().isEmpty()) { h += lineHeight; continue; }
            StringBuilder sb = new StringBuilder();
            for (String word : raw.split(" ")) {
                if (fm.stringWidth(sb.toString() + word) > maxWidth) { h += lineHeight; sb.setLength(0); }
                sb.append(word).append(" ");
            }
            h += lineHeight;
        }
        return h;
    }

    void initializeWauSelectionBoxes() {
        wauSelectionBoxes.clear();
        int startX = 80, startY = 360;
        for (int i = 0; i < WauType.values().length; i++) {
            int col = i % 3, row = i / 3;
            wauSelectionBoxes.add(new Rectangle(startX + col * (WAU_BOX_SIZE + WAU_BOX_GAP), startY + row * (WAU_BOX_SIZE + WAU_BOX_GAP + 25), WAU_BOX_SIZE, WAU_BOX_SIZE));
        }
    }

    private void syncSelectedIndexWithCurrent() {
        for (int i = 0; i < WauType.values().length; i++) {
            if (WauType.values()[i] == currentWauType) { wauSelectedIndex = i; break; }
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
        } catch (Exception e) {}
    }

    void loadSounds() {
        jumpSound = loadClip("jump.wav"); hitSound = loadClip("hit.wav");
        scoreSound = loadClip("score.wav"); bgMusic = loadClip("music.wav");
    }

    Clip loadClip(String path) {
        try {
            AudioInputStream ais = AudioSystem.getAudioInputStream(getClass().getClassLoader().getResource(path));
            Clip clip = AudioSystem.getClip(); clip.open(ais); return clip;
        } catch (Exception e) { return null; }
    }

    void playSound(Clip clip) {
        if (clip == null || muted) return;
        if (clip.isRunning()) clip.stop();
        clip.setFramePosition(0); clip.start();
    }

    private void drawSelectionOverlay(Graphics g, Graphics2D g2) {
        g.setColor(new Color(0, 0, 0, 220)); g.fillRect(0, 0, WIDTH, HEIGHT);
        WauType selectedWau = WauType.values()[wauSelectedIndex];
        if (showWauDetails) drawWauDetailsPage(g2, g, selectedWau);
        else {
            g.setFont(new Font("Arial", Font.BOLD, 38)); g.setColor(new Color(255, 215, 0)); g.drawString("SELECT YOUR WAU", 110, 35);
            int pSize = 180, pX = (WIDTH - pSize) / 2;
            
            g.setColor(Color.YELLOW);
            g2.setStroke(new BasicStroke(4));
            g2.drawRoundRect(pX, 60, pSize, pSize, 24, 24);
            g.setColor(new Color(255, 255, 255, 30)); g.fillRoundRect(pX, 60, pSize, pSize, 24, 24);
            
            try { g.drawImage(new ImageIcon(getClass().getClassLoader().getResource(selectedWau.image)).getImage(), pX + 18, 78, pSize - 36, pSize - 36, null); } catch (Exception e) {}
            g.setFont(new Font("Arial", Font.BOLD, 28)); g.setColor(Color.WHITE);
            String name = selectedWau.getDisplayName(); g.drawString(name, pX + (pSize - g.getFontMetrics().stringWidth(name)) / 2, 288);
            
            Shape old = g.getClip(); g.setClip(40, 320, WIDTH - 80, 320);
            for (int i = 0; i < WauType.values().length; i++) {
                Rectangle box = wauSelectionBoxes.get(i); int drawY = box.y - wauScrollOffset;
                
                if (i == wauSelectedIndex) {
                    g.setColor(Color.YELLOW);
                    g2.setStroke(new BasicStroke(4));
                } else {
                    g.setColor(Color.WHITE);
                    g2.setStroke(new BasicStroke(2));
                }
                g2.drawRoundRect(box.x, drawY, box.width, box.height, 10, 10);
                
                try { g.drawImage(new ImageIcon(getClass().getClassLoader().getResource(WauType.values()[i].image)).getImage(), box.x + 10, drawY + 10, box.width - 20, box.height - 20, null); } catch (Exception e) {}
            }
            g.setClip(old);
        }
        drawSelectionButtons(g);
    }

    private void drawMainMenu(Graphics g, Graphics2D g2) {
        g.setColor(new Color(0, 0, 0, 100)); g.fillRect(0, 0, WIDTH, HEIGHT);
        
        g.setFont(new Font("Arial", Font.BOLD, 60)); g.setColor(Color.BLACK); g.drawString("WAU BULAN", 132, 152);
        g.setColor(new Color(255, 215, 0)); g.drawString("WAU BULAN", 130, 150);
        
        g.setFont(new Font("Arial", Font.ITALIC, 24)); g.setColor(Color.LIGHT_GRAY);
        String subtitle = "A Traditional Malaysian Game";
        g.drawString(subtitle, (WIDTH - g.getFontMetrics().stringWidth(subtitle)) / 2, 190);
        
        g.setFont(new Font("Arial", Font.BOLD, 30)); g.setColor(Color.WHITE);
        String best = "Best Score: " + highScore;
        g.drawString(best, (WIDTH - g.getFontMetrics().stringWidth(best)) / 2, 235);
        
        g.setColor(new Color(34, 139, 34, 220)); g.fillRoundRect(startButton.x, startButton.y, startButton.width, startButton.height, 25, 25);
        g.setColor(new Color(70, 130, 180, 220)); g.fillRoundRect(wauButton.x, wauButton.y, wauButton.width, wauButton.height, 25, 25);
        g.setColor(Color.WHITE); g.setFont(new Font("Arial", Font.BOLD, 45)); g.drawString("START GAME", 155, 410);
        g.setFont(new Font("Arial", Font.BOLD, 40)); g.drawString("CHOOSE WAU", 160, 500);
    }

    private void drawGameOverMenu(Graphics g, Graphics2D g2) {
        g.setColor(new Color(0, 0, 0, 150)); g.fillRect(0, 0, WIDTH, HEIGHT);
        g.setFont(new Font("Arial", Font.BOLD, 60)); g.setColor(Color.RED); g.drawString("GAME OVER", 115, 250);
        
        g.setFont(new Font("Arial", Font.BOLD, 40)); g.setColor(Color.WHITE);
        String s = "Score: " + score; g.drawString(s, (WIDTH - g.getFontMetrics().stringWidth(s)) / 2, 350);
        
        // --- Requirement 3: Best Score / New Record Logic (Updated to Yellow) ---
        String bestStr = showNewRecordText ? "New Record!" : "Best: " + highScore;
        
        // Set color to yellow if it is a new record
        if (showNewRecordText) {
            g.setColor(Color.YELLOW);
        } else {
            g.setColor(Color.WHITE);
        }
        
        g.setFont(new Font("Arial", Font.BOLD, 28));
        g.drawString(bestStr, (WIDTH - g.getFontMetrics().stringWidth(bestStr)) / 2, 400);

        g.setColor(new Color(255, 140, 0, 220)); g.fillRoundRect(restartButton.x, restartButton.y, restartButton.width, restartButton.height, 25, 25);
        g.setColor(new Color(70, 130, 180, 220)); g.fillRoundRect(gameOverWauButton.x, gameOverWauButton.y, gameOverWauButton.width, gameOverWauButton.height, 25, 25);
        g.setColor(Color.WHITE); g.setFont(new Font("Arial", Font.BOLD, 40));
        g.drawString("RESTART", 210, 475); g.drawString("CHOOSE WAU", 160, 575);
    }

    private void drawReadyPrompt(Graphics g) {
        g.setFont(new Font("Arial", Font.BOLD, 35)); g.setColor(new Color(0, 0, 0, 180));
        g.fillRoundRect(100, 320, 400, 120, 20, 20); g.setColor(Color.WHITE); g.drawString("Press SPACE to START", 105, 395);
    }

    private void drawSelectionButtons(Graphics g) {
        g.setFont(new Font("Arial", Font.BOLD, 28));
        g.setColor(new Color(200, 50, 50, 220)); g.fillRoundRect(wauBackButton.x, wauBackButton.y, wauBackButton.width, wauBackButton.height, 20, 20);
        g.setColor(new Color(65, 140, 200, 220)); g.fillRoundRect(wauSelectButton.x, wauSelectButton.y, wauSelectButton.width, wauSelectButton.height, 20, 20);
        g.setColor(Color.WHITE); String bt = showWauDetails ? "CANCEL" : "BACK";
        g.drawString(bt, wauBackButton.x + (wauBackButton.width - g.getFontMetrics().stringWidth(bt)) / 2, wauBackButton.y + 40);
        g.drawString("SELECT", wauSelectButton.x + (wauSelectButton.width - g.getFontMetrics().stringWidth("SELECT")) / 2, wauSelectButton.y + 40);
    }

    @Override public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_SPACE) {
            if (readyToStart && !gameStarted) {
                gameStarted = true; shieldActive = (currentWauType == WauType.WAU_DODO_HELANG || currentWauType == WauType.WAU_BARAT || currentWauType == WauType.WAU_KEBAYAK);
                currentObstacleSpeed = (currentWauType == WauType.WAU_MERAK || currentWauType == WauType.WAU_KAPAL || currentWauType == WauType.WAU_KANGKANG) ? 2 : 3;
                if (bgMusic != null) bgMusic.loop(Clip.LOOP_CONTINUOUSLY); obstacles.clear(); spawnObstacles();
            } else if (gameStarted && !gameOver) { velocity = JUMP_VELOCITY; playSound(jumpSound); }
        } else if (e.getKeyCode() == KeyEvent.VK_P) paused = !paused;
        else if (e.getKeyCode() == KeyEvent.VK_M) muted = !muted;
    }

    private void handlePointerClick(int mouseX, int mouseY) {
        if (showWauSelection) {
            if (wauBackButton.contains(mouseX, mouseY)) { if (showWauDetails) showWauDetails = false; else showWauSelection = false; }
            else if (wauSelectButton.contains(mouseX, mouseY)) { if (showWauDetails) { currentWauType = WauType.values()[wauSelectedIndex]; loadImages(currentWauType); showWauSelection = false; showWauDetails = false; } else showWauDetails = true; }
            else if (!showWauDetails) { for (int i = 0; i < WauType.values().length; i++) { if (new Rectangle(wauSelectionBoxes.get(i).x, wauSelectionBoxes.get(i).y - wauScrollOffset, WAU_BOX_SIZE, WAU_BOX_SIZE).contains(mouseX, mouseY)) wauSelectedIndex = i; } }
        } else if (!readyToStart && !gameStarted) { if (startButton.contains(mouseX, mouseY)) readyToStart = true; if (wauButton.contains(mouseX, mouseY)) { showWauSelection = true; syncSelectedIndexWithCurrent(); } }
        else if (gameOver) { if (restartButton.contains(mouseX, mouseY)) { gameOver = false; gameStarted = false; readyToStart = true; score = 0; y = 300; velocity = 0; obstacles.clear(); showNewRecordText = false; } if (gameOverWauButton.contains(mouseX, mouseY)) { showWauSelection = true; syncSelectedIndexWithCurrent(); } }
        repaint();
    }

    @Override public void mousePressed(MouseEvent e) { handlePointerClick(e.getX(), e.getY()); }
    
    @Override public void mouseMoved(MouseEvent e) { 
        hoveredButton = new Rectangle(e.getX(), e.getY(), 1, 1); 
        if (gameOver && showNewRecordText) {
            showNewRecordText = false;
        }
        repaint(); 
    }
    
    @Override public void mouseClicked(MouseEvent e) {}
    @Override public void mouseReleased(MouseEvent e) {}
    @Override public void mouseEntered(MouseEvent e) {}
    @Override public void mouseExited(MouseEvent e) {}
    @Override public void mouseDragged(MouseEvent e) {}
    @Override public void keyReleased(KeyEvent e) {}
    @Override public void keyTyped(KeyEvent e) {}
}