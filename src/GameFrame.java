import javax.swing.*;

public class GameFrame extends JFrame {

    public GameFrame(WauType type) {
        this.add(new GamePanel(type));
        this.setTitle("Flappy Wau Malaysia");
        this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        this.setResizable(false);
        this.pack();
        this.setLocationRelativeTo(null);
        this.setVisible(true);
    }
}
