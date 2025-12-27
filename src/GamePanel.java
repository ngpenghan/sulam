import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Random;
import javax.sound.sampled.*;

public class GamePanel extends JPanel implements ActionListener, KeyListener, MouseListener {
    private final static int WIDTH = 600;
    private final static int HEIGHT = 800;
    private final static int GROUND_HEIGHT = 54; // Set to your ground.png height!
    private final static int POLE_WIDTH = 320;   // Very wide pipe effect!
    private final static int POLE_HEIGHT = 525;  // Tall!
    private final static int POLE_OVERLAP = 50;  // Amount pole sinks into ground for perfect "rooted" appearance

    private final static int GRAVITY = 1;
    private final static int JUMP_VELOCITY = -12;
    private final static int OBSTACLE_SPEED = 4;
    private final static int BIRD_WIDTH = 320;
    private final static int MIN_OBSTACLE_HEIGHT = 90, MAX_OBSTACLE_HEIGHT = 350;
    private final static int GAP = 240;
    private final static int WAW_WIDTH = 130, WAW_HEIGHT = 130;

    private int x = 100, y = 300, velocity = 0;
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
    private final Random random = new Random();
    private ArrayList<Rectangle> birds = new ArrayList<>();
    private ArrayList<Rectangle> poles = new ArrayList<>();
    private Clip jumpSound, hitSound, scoreSound, bgMusic;
    
    private Rectangle startButton = new Rectangle(150, 350, 300, 80);
    private Rectangle wauButton = new Rectangle(150, 450, 300, 80);
    private Rectangle restartButton = new Rectangle(150, 420, 300, 80);
    
    private boolean showWauSelection = false;
    private WauType currentWauType;
    private ArrayList<Rectangle> wauSelectionBoxes = new ArrayList<>();

    // --- HITBOX TUNING VARIABLES ---
    // Adjust this to cut more or less transparency from the top of the pole image
    private final int POLE_TOP_CUT = 140; 
    private final int POLE_BOTTOM_CUT = 30;

    public GamePanel(WauType type) {
        this.setPreferredSize(new Dimension(WIDTH, HEIGHT));
        this.setFocusable(true);
        this.addKeyListener(this);
        this.addMouseListener(this);
        this.currentWauType = type;

        loadImages(type);
        initializeWauSelectionBoxes();
        loadSounds();
        timer = new Timer(10, this);
        timer.start();
    }

    void initializeWauSelectionBoxes() {
        // Create 7 boxes for 7 wau types in a grid (3 columns)
        int boxSize = 120;
        int startX = 80;
        int startY = 150;
        int gap = 20;
        
        for (int i = 0; i < WauType.values().length; i++) {
            int col = i % 3;
            int row = i / 3;
            int x = startX + col * (boxSize + gap);
            int y = startY + row * (boxSize + gap);
            wauSelectionBoxes.add(new Rectangle(x, y, boxSize, boxSize));
        }
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
            poleImg = new ImageIcon(cl.getResource("lightpole.png")).getImage();
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
        // Stand the pole so it "sinks" POLE_OVERLAP px into ground/grass
        int poleY = (HEIGHT - GROUND_HEIGHT) - (POLE_HEIGHT - POLE_OVERLAP);
        poles.add(new Rectangle(WIDTH, poleY, POLE_WIDTH, POLE_HEIGHT));
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (!gameStarted || gameOver || paused) return;
        velocity += GRAVITY;
        y += velocity;

        for (int i = 0; i < birds.size(); i++) {
            birds.get(i).x -= OBSTACLE_SPEED;
            poles.get(i).x -= OBSTACLE_SPEED;
            if (birds.get(i).x + BIRD_WIDTH == x) {
                score++;
                playSound(scoreSound);
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
            // Draw wau selection screen
            g.setColor(new Color(0, 0, 0, 200));
            g.fillRect(0, 0, WIDTH, HEIGHT);
            
            g.setFont(new Font("Arial", Font.BOLD, 40));
            g.setColor(Color.WHITE);
            g.drawString("SELECT YOUR WAU", 130, 100);
            
            // Draw wau selection boxes
            WauType[] wauTypes = WauType.values();
            for (int i = 0; i < wauTypes.length; i++) {
                Rectangle box = wauSelectionBoxes.get(i);
                
                // Highlight selected wau
                if (wauTypes[i] == currentWauType) {
                    g.setColor(new Color(255, 215, 0, 200));
                    g.fillRoundRect(box.x - 5, box.y - 5, box.width + 10, box.height + 10, 15, 15);
                }
                
                // Draw box border
                g.setColor(Color.WHITE);
                g2.setStroke(new BasicStroke(3));
                g2.drawRoundRect(box.x, box.y, box.width, box.height, 10, 10);
                
                // Draw wau image
                try {
                    Image wauImg = new ImageIcon(getClass().getClassLoader().getResource(wauTypes[i].image)).getImage();
                    g.drawImage(wauImg, box.x + 10, box.y + 10, box.width - 20, box.height - 20, null);
                } catch (Exception e) {
                    g.setColor(Color.GRAY);
                    g.fillRoundRect(box.x + 10, box.y + 10, box.width - 20, box.height - 20, 5, 5);
                }
            }
            
            // Draw back button
            g.setColor(new Color(200, 50, 50, 220));
            g.fillRoundRect(200, 650, 200, 60, 15, 15);
            g.setColor(Color.WHITE);
            g.setFont(new Font("Arial", Font.BOLD, 30));
            g.drawString("BACK", 260, 690);
            
        } else if (!gameStarted && !readyToStart) {
            // Draw Start Button
            g.setColor(new Color(34, 139, 34, 220));
            g.fillRoundRect(startButton.x, startButton.y, startButton.width, startButton.height, 20, 20);
            g.setColor(Color.WHITE);
            g2.setStroke(new BasicStroke(3));
            g2.drawRoundRect(startButton.x, startButton.y, startButton.width, startButton.height, 20, 20);
            
            g.setFont(new Font("Arial", Font.BOLD, 40));
            g.setColor(Color.WHITE);
            g.drawString("START GAME", 165, 400);
            
            // Draw Wau Selection Button
            g.setColor(new Color(70, 130, 180, 220));
            g.fillRoundRect(wauButton.x, wauButton.y, wauButton.width, wauButton.height, 20, 20);
            g.setColor(Color.WHITE);
            g2.setStroke(new BasicStroke(3));
            g2.drawRoundRect(wauButton.x, wauButton.y, wauButton.width, wauButton.height, 20, 20);
            
            g.setFont(new Font("Arial", Font.BOLD, 40));
            g.setColor(Color.WHITE);
            g.drawString("CHOOSE WAU", 165, 500);
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
            g.setFont(new Font("Arial", Font.BOLD, 50));
            g.setColor(Color.BLACK);
            g.drawString("GAME OVER", 152, 302);
            g.setColor(Color.RED);
            g.drawString("GAME OVER", 150, 300);
            
            // Draw Restart Button
            g.setColor(new Color(255, 140, 0, 220));
            g.fillRoundRect(restartButton.x, restartButton.y, restartButton.width, restartButton.height, 20, 20);
            g.setColor(Color.WHITE);
            g2.setStroke(new BasicStroke(3));
            g2.drawRoundRect(restartButton.x, restartButton.y, restartButton.width, restartButton.height, 20, 20);
            
            g.setFont(new Font("Arial", Font.BOLD, 35));
            g.setColor(Color.WHITE);
            g.drawString("RESTART", 210, 470);
            
            // Draw Wau Selection Button below Restart
            g.setColor(new Color(70, 130, 180, 220));
            g.fillRoundRect(wauButton.x, wauButton.y + 70, wauButton.width, wauButton.height, 20, 20);
            g.setColor(Color.WHITE);
            g2.setStroke(new BasicStroke(3));
            g2.drawRoundRect(wauButton.x, wauButton.y + 70, wauButton.width, wauButton.height, 20, 20);
            
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
    
    @Override
    public void mouseClicked(MouseEvent e) {
        int mouseX = e.getX();
        int mouseY = e.getY();
        
        if (showWauSelection) {
            // Check if clicked on a wau selection box
            WauType[] wauTypes = WauType.values();
            for (int i = 0; i < wauTypes.length; i++) {
                if (wauSelectionBoxes.get(i).contains(mouseX, mouseY)) {
                    currentWauType = wauTypes[i];
                    loadImages(currentWauType);
                    repaint();
                    return;
                }
            }
            
            // Check if clicked on back button
            Rectangle backButton = new Rectangle(200, 650, 200, 60);
            if (backButton.contains(mouseX, mouseY)) {
                showWauSelection = false;
                repaint();
            }
        } else if (!readyToStart && !gameStarted && startButton.contains(mouseX, mouseY)) {
            readyToStart = true;
            repaint();
        } else if (!readyToStart && !gameStarted && wauButton.contains(mouseX, mouseY)) {
            showWauSelection = true;
            repaint();
        } else if (gameOver) {
            if (restartButton.contains(mouseX, mouseY)) {
                // Reset game and show "Press SPACE to start"
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
            } else if (new Rectangle(wauButton.x, wauButton.y + 70, wauButton.width, wauButton.height).contains(mouseX, mouseY)) {
                showWauSelection = true;
                repaint();
            }
        }
    }
    
    @Override public void mousePressed(MouseEvent e) {
        int mouseX = e.getX();
        int mouseY = e.getY();
        
        if (showWauSelection) {
            // Check if clicked on a wau selection box
            WauType[] wauTypes = WauType.values();
            for (int i = 0; i < wauTypes.length; i++) {
                if (wauSelectionBoxes.get(i).contains(mouseX, mouseY)) {
                    currentWauType = wauTypes[i];
                    loadImages(currentWauType);
                    repaint();
                    return;
                }
            }
            
            // Check if clicked on back button
            Rectangle backButton = new Rectangle(200, 650, 200, 60);
            if (backButton.contains(mouseX, mouseY)) {
                showWauSelection = false;
                repaint();
            }
        } else if (!readyToStart && !gameStarted && startButton.contains(mouseX, mouseY)) {
            readyToStart = true;
            repaint();
        } else if (!readyToStart && !gameStarted && wauButton.contains(mouseX, mouseY)) {
            showWauSelection = true;
            repaint();
        } else if (gameOver) {
            if (restartButton.contains(mouseX, mouseY)) {
                // Reset game and show "Press SPACE to start"
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
            } else if (new Rectangle(wauButton.x, wauButton.y + 70, wauButton.width, wauButton.height).contains(mouseX, mouseY)) {
                showWauSelection = true;
                repaint();
            }
        }
    }
    @Override public void mouseReleased(MouseEvent e) {}
    @Override public void mouseEntered(MouseEvent e) {}
    @Override public void mouseExited(MouseEvent e) {}
}