package pl.poznan.put.gui.window;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;

public class DialogAbout extends JDialog {
    static final Logger LOGGER = LoggerFactory.getLogger(DialogAbout.class);
    private static final long serialVersionUID = 1L;

    public DialogAbout(Frame owner) {
        super(owner, true);

        JEditorPane editorPane = new JEditorPane();
        editorPane.setContentType("text/html");
        editorPane.setEditable(false);

        URL resource = getClass().getResource("/about.html");
        try (InputStream stream = resource.openStream()) {
            editorPane.setText(IOUtils.toString(stream, "UTF-8"));
            editorPane.setCaretPosition(0);
        } catch (IOException e) {
            DialogAbout.LOGGER.error("Failed to load 'About' text", e);
        }

        JScrollPane scrollPane = new JScrollPane(editorPane);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        BufferedImage image = null;
        JLabel labelImage = new JLabel();

        try {
            resource = getClass().getResource("/rabit.png");
            image = ImageIO.read(resource);
            labelImage = new JLabel(new ImageIcon(image));
        } catch (IOException e) {
            DialogAbout.LOGGER.error("Failed to load RABIT logo image", e);
        }

        JButton buttonClose = new JButton("Close");
        JPanel panelClose = new JPanel();
        panelClose.add(buttonClose);

        JPanel panelImage = new JPanel(new BorderLayout());
        panelImage.add(labelImage, BorderLayout.CENTER);

        setLayout(new BorderLayout());
        add(panelImage, BorderLayout.WEST);
        add(scrollPane, BorderLayout.CENTER);
        add(panelClose, BorderLayout.SOUTH);

        setTitle("MCQ4Structures: about");

        setPreferredSize(new Dimension(660, 510));
        pack();

        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        int x = screenSize.width - 660;
        int y = screenSize.height - 510;
        setLocation(x / 2, y / 2);

        panelImage.setBackground(Color.white);

        editorPane.addHyperlinkListener(new HyperlinkListener() {
            @Override
            public void hyperlinkUpdate(HyperlinkEvent e) {
                if (e == null) {
                    return;
                }

                if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED
                    && Desktop.isDesktopSupported()) {
                    try {
                        Desktop.getDesktop().browse(e.getURL().toURI());
                    } catch (IOException | URISyntaxException e1) {
                        DialogAbout.LOGGER
                                .error("Failed to browse URL: " + e.getURL(),
                                       e1);
                    }
                }
            }
        });

        buttonClose.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent arg0) {
                dispose();
            }
        });
    }
}
