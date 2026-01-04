
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Random;
import javax.sound.sampled.*;

public class GamePanel extends JPanel implements ActionListener, KeyListener, MouseListener, MouseMotionListener {
    private static final int WIDTH = 600;
    private static final int HEIGHT = 800;
    private static final int GROUND_HEIGHT = 54;
    private static final int POLE_WIDTH = 300;
    private static final int POLE_HEIGHT = 500;
    private static final int POLE_OVERLAP = 70;

    // Lighter gravity & capped fall
    private static final double GRAVITY = 0.6;
    private static final double JUMP_VELOCITY = -10.0;
    private static final double MAX_FALL_SPEED = 12.0;

    private static final int OBSTACLE_SPEED = 4;
    private static final int BIRD_WIDTH = 250;
    private static final int BIRD_HEIGHT = 250;
    private static final int GAP = 240;
    private static final int WAW_WIDTH = 130, WAW_HEIGHT = 130;

    private int x = 100;
    private double y = 300;
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
    private final ArrayList<Rectangle> birds = new ArrayList<>();
    private final ArrayList<Rectangle> poles = new ArrayList<>();
    // Track if a bird obstacle has been scored already
    private final ArrayList<Boolean> birdScored = new ArrayList<>();

    private Clip jumpSound, hitSound, scoreSound, bgMusic;
    private int menuAnimationCounter = 0;
    private Rectangle hoveredButton = null;

    private final Rectangle startButton = new Rectangle(150, 350, 300, 80);
    private final Rectangle wauButton = new Rectangle(150, 450, 300, 80);
    private final Rectangle restartButton = new Rectangle(150, 420, 300, 80);

    private boolean showWauSelection = false;
    private WauType currentWauType;
    private final ArrayList<Rectangle> wauSelectionBoxes = new ArrayList<>();

    private final int POLE_TOP_CUT = 80;
    private final int POLE_BOTTOM_CUT = 30;

    // Falling animation state
    private double wauAngleDeg = 0.0;

    public GamePanel(WauType type) {
        setPreferredSize(new Dimension(WIDTH, HEIGHT));
        setFocusable(true);
        addKeyListener(this);
        addMouseListener(this);
        addMouseMotionListener(this);

        currentWauType = type;
        loadImages(type);
        initializeWauSelectionBoxes();
        loadSounds();

        timer = new Timer(10, this);
        timer.start();
    }

    void initializeWauSelectionBoxes() {
        int boxSize = 120;
        int startX = 80;
        int startY = 150;
        int gap = 30;

        for (int i = 0; i < WauType.values().length; i++) {
            int col = i % 3;
            int row = i / 3;
            int bx = startX + col * (boxSize + gap);
            int by = startY + row * (boxSize + gap + 25);
            wauSelectionBoxes.add(new Rectangle(bx, by, boxSize, boxSize));
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

Image loadImage(String name) {
        try {
            ClassLoader cl = getClass().getClassLoader();
            java.net.URL url = cl.getResource(name);
            if (url != null) return new ImageIcon(url).getImage();
            java.io.File f = new java.io.File(name);
            if (f.exists()) return new ImageIcon(f.getAbsolutePath()).getImage();
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
            AudioInputStream ais = AudioSystem.getAudioInputStream(getClass().getClassLoader().getResource(path));
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
        int poleY = (HEIGHT - GROUND_HEIGHT) - (POLE_HEIGHT - POLE_OVERLAP);
        poles.add(new Rectangle(WIDTH, poleY, POLE_WIDTH, POLE_HEIGHT));

        int maxBirdY = poleY - GAP - BIRD_HEIGHT;
        if (maxBirdY < 0) maxBirdY = 0;
        int birdY = random.nextInt(maxBirdY + 1);
        birds.add(new Rectangle(WIDTH, birdY, BIRD_WIDTH, BIRD_HEIGHT));
        birdScored.add(false);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (!gameStarted || gameOver || paused) return;
        menuAnimationCounter++;

        velocity += GRAVITY;
        if (velocity > MAX_FALL_SPEED) velocity = MAX_FALL_SPEED;
        y += velocity;

        // Animate falling/tilt: tilt up on jump, gradually tilt down when falling
        if (velocity < -1.5) {
            wauAngleDeg = Math.max(-25, wauAngleDeg - 3);
        } else {
            wauAngleDeg = Math.min(35, wauAngleDeg + 2);
        }

        for (int i = 0; i < birds.size(); i++) {
            birds.get(i).x -= OBSTACLE_SPEED;
            poles.get(i).x -= OBSTACLE_SPEED;

            // Score when the wau has fully passed the bird obstacle
            if (!birdScored.get(i) && birds.get(i).x + BIRD_WIDTH < x) {
                birdScored.set(i, true);
                score++;
                playSound(scoreSound);
                // no sleep needed; keep loop smooth
            }
        }

        if (!birds.isEmpty() && birds.get(0).x < -BIRD_WIDTH) {
            birds.remove(0);
            poles.remove(0);
            birdScored.remove(0);
            spawnObstacles();
        }
        checkCollision();
        repaint();
    }

    void checkCollision() {
        int wauInsetX = 45, wauInsetY = 44;
        Rectangle wau = new Rectangle(
            x + wauInsetX, (int) y + wauInsetY,
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

        for (Rectangle r : birds) {
            if (birdImg != null) g.drawImage(birdImg, r.x, r.y, r.width, r.height, null);
            else { g.setColor(Color.RED); g.fillRect(r.x, r.y, r.width, r.height); }
        }
        for (Rectangle r : poles) {
            if (poleImg != null) g.drawImage(poleImg, r.x, r.y, POLE_WIDTH, POLE_HEIGHT, null);
            else { g.setColor(Color.GREEN); g.fillRect(r.x, r.y, POLE_WIDTH, POLE_HEIGHT); }
        }

        // Draw wau with rotation for falling animation
        Graphics2D g2 = (Graphics2D) g.create();
        double cx = x + WAW_WIDTH / 2.0;
        double cy = y + WAW_HEIGHT / 2.0;
        g2.rotate(Math.toRadians(wauAngleDeg), cx, cy);
        g2.drawImage(wauImage, x, (int) y, WAW_WIDTH, WAW_HEIGHT, null);
        g2.dispose();

        int groundY = HEIGHT - GROUND_HEIGHT;
        g.drawImage(groundImg, 0, groundY, WIDTH, GROUND_HEIGHT, null);

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

        g2 = (Graphics2D) g;

        if (showWauSelection) {
            g.setColor(new Color(0, 0, 0, 220));
            g.fillRect(0, 0, WIDTH, HEIGHT);

            g.setFont(new Font("Arial", Font.BOLD, 50));
            g.setColor(new Color(255, 215, 0));
            g.drawString("SELECT YOUR WAU", 85, 100);

            g.setFont(new Font("Arial", Font.PLAIN, 20));
            g.setColor(new Color(200, 200, 200));
            g.drawString("Choose your kite type", 175, 130);

            WauType[] wauTypes = WauType.values();
            for (int i = 0; i < wauTypes.length; i++) {
                Rectangle box = wauSelectionBoxes.get(i);

                if (wauTypes[i] == currentWauType) {
                    g.setColor(new Color(255, 215, 0, 100));
                    g.fillRoundRect(box.x - 8, box.y - 8, box.width + 16, box.height + 16, 20, 20);
                    g2.setColor(new Color(255, 215, 0));
                    g2.setStroke(new BasicStroke(5));
                    g2.drawRoundRect(box.x - 5, box.y - 5, box.width + 10, box.height + 10, 15, 15);
                }

                g.setColor(Color.WHITE);
                g2.setStroke(new BasicStroke(3));
                g2.drawRoundRect(box.x, box.y, box.width, box.height, 10, 10);

                try {
                    Image wauImg = new ImageIcon(getClass().getClassLoader().getResource(wauTypes[i].image)).getImage();
                    g.drawImage(wauImg, box.x + 10, box.y + 10, box.width - 20, box.height - 20, null);
                } catch (Exception e) {
                    g.setColor(Color.GRAY);
                    g.fillRoundRect(box.x + 10, box.y + 10, box.width - 20, box.height - 20, 5, 5);
                }

                g.setFont(new Font("Arial", Font.PLAIN, 14));
                g.setColor(Color.WHITE);
                String wauName = wauTypes[i].toString().replace('_', ' ');
                FontMetrics fm = g.getFontMetrics();
                int textX = box.x + (box.width - fm.stringWidth(wauName)) / 2;
                g.drawString(wauName, textX, box.y + box.height + 25);
            }

            Rectangle backButton = new Rectangle(200, 650, 200, 60);
            boolean backHovered = hoveredButton != null && backButton.contains(hoveredButton);
            g.setColor(backHovered ? new Color(220, 80, 80, 240) : new Color(200, 50, 50, 220));
            g.fillRoundRect(backButton.x, backButton.y, backButton.width, backButton.height, 20, 20);
            g.setColor(Color.WHITE);
            g2.setStroke(new BasicStroke(backHovered ? 4 : 3));
            g2.drawRoundRect(backButton.x, backButton.y, backButton.width, backButton.height, 20, 20);

            g.setFont(new Font("Arial", Font.BOLD, 30));
            g.setColor(Color.WHITE);
            g.drawString("BACK", 260, 690);
        } else if (!gameStarted && !readyToStart) {
            g.setColor(new Color(0, 0, 0, 100));
            g.fillRect(0, 0, WIDTH, HEIGHT);

            g.setFont(new Font("Arial", Font.BOLD, 60));
            g.setColor(new Color(0, 0, 0, 200));
            g.drawString("FLAPPY WAU", 132, 152);
            g.setColor(new Color(255, 215, 0));
            g.drawString("FLAPPY WAU", 130, 150);

            g.setFont(new Font("Arial", Font.ITALIC, 24));
            g.setColor(new Color(200, 200, 200));
            g.drawString("A Traditional Malaysian Game", 155, 190);

            g.setFont(new Font("Arial", Font.BOLD, 28));
            g.setColor(Color.WHITE);
            g.drawString("Best Score: " + highScore, 170, 230);

            boolean startHovered = hoveredButton != null && startButton.contains(hoveredButton);
            g.setColor(startHovered ? new Color(50, 180, 50, 240) : new Color(34, 139, 34, 220));
            g.fillRoundRect(startButton.x, startButton.y, startButton.width, startButton.height, 25, 25);
            g.setColor(Color.WHITE);
            g2.setStroke(new BasicStroke(startHovered ? 4 : 3));
            g2.drawRoundRect(startButton.x, startButton.y, startButton.width, startButton.height, 25, 25);

            g.setFont(new Font("Arial", Font.BOLD, 45));
            g.setColor(Color.WHITE);
            g.drawString("START GAME", 155, 410);

            boolean wauHovered = hoveredButton != null && wauButton.contains(hoveredButton);
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

            boolean restartHovered = hoveredButton != null && restartButton.contains(hoveredButton);
            g.setColor(restartHovered ? new Color(255, 160, 20, 240) : new Color(255, 140, 0, 220));
            g.fillRoundRect(restartButton.x, restartButton.y, restartButton.width, restartButton.height, 25, 25);
            g.setColor(Color.WHITE);
            g2.setStroke(new BasicStroke(restartHovered ? 4 : 3));
            g2.drawRoundRect(restartButton.x, restartButton.y, restartButton.width, restartButton.height, 25, 25);

            g.setFont(new Font("Arial", Font.BOLD, 40));
            g.setColor(Color.WHITE);
            g.drawString("RESTART", 210, 475);

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
                gameStarted = true;
                if (bgMusic != null) bgMusic.loop(Clip.LOOP_CONTINUOUSLY);
                spawnObstacles();
            } else if (gameStarted && !gameOver) {
                velocity = JUMP_VELOCITY;
                wauAngleDeg = -20; // tilt up on jump
                playSound(jumpSound);
                try { Thread.sleep(80); } catch (InterruptedException ignored) {}
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
            WauType[] wauTypes = WauType.values();
            for (int i = 0; i < wauTypes.length; i++) {
                if (wauSelectionBoxes.get(i).contains(mouseX, mouseY)) {
                    currentWauType = wauTypes[i];
                    loadImages(currentWauType);
                    repaint();
                    return;
                }
            }

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
                gameOver = false;
                gameStarted = false;
                readyToStart = true;
                paused = false;
                score = 0;
                y = 300;
                velocity = 0;
                wauAngleDeg = 0;
                birds.clear();
                poles.clear();
                birdScored.clear();
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

    @Override public void mousePressed(MouseEvent e) {}
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
            if (startButton.contains(mouseX, mouseY)) hoveredButton = startButton;
            else if (wauButton.contains(mouseX, mouseY)) hoveredButton = wauButton;
        } else if (gameOver) {
            if (restartButton.contains(mouseX, mouseY)) hoveredButton = restartButton;
            else if (new Rectangle(wauButton.x, wauButton.y + 70, wauButton.width, wauButton.height).contains(mouseX, mouseY))
                hoveredButton = new Rectangle(wauButton.x, wauButton.y + 70, wauButton.width, wauButton.height);
        } else if (showWauSelection) {
            Rectangle backButton = new Rectangle(200, 650, 200, 60);
            if (backButton.contains(mouseX, mouseY)) hoveredButton = backButton;
        }

        if ((oldHovered == null && hoveredButton != null) ||
            (oldHovered != null && hoveredButton == null) ||
            (oldHovered != hoveredButton)) {
            repaint();
        }
    }

    @Override public void mouseDragged(MouseEvent e) {}
}
