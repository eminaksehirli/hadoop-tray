package com.dataspark.hadoop_tray;

import static java.util.Arrays.asList;

import java.awt.BorderLayout;
import java.awt.event.ActionListener;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SpringLayout;
import layout.SpringUtilities;

public class OptionsPane extends JFrame {
  private static final long serialVersionUID = -6334943347610507129L;

  private String knownServer;
  private String knownUser;
  private int knownLongPeriod;
  private int knownShortPeriod;

  JTextField serverTF;
  JTextField userTF;
  JTextField longTF;
  JTextField shortTF;

  JButton okBtn;
  JButton cancelBtn;

  private JPanel mainPane;

  public OptionsPane(String knownServer, String knownUser, int knownLongPeriod,
      int knownShortPeriod) {
    this.knownServer = knownServer;
    this.knownUser = knownUser;
    this.knownLongPeriod = knownLongPeriod;
    this.knownShortPeriod = knownShortPeriod;
  }

  void show(ActionListener saveAction) {
    getContentPane().setLayout(new BorderLayout());
    SpringLayout layout = new SpringLayout();
    mainPane = new JPanel(layout);

    serverTF = new JTextField(knownServer, 40);
    userTF = new JTextField(knownUser, 40);
    longTF = new JTextField("" + knownLongPeriod, 40);
    shortTF = new JTextField("" + knownShortPeriod, 40);

    okBtn = new JButton("OK");
    cancelBtn = new JButton("Cancel");

    asList(serverTF, userTF, longTF, shortTF).forEach(e -> e.addActionListener(saveAction));
    okBtn.addActionListener(saveAction);
    cancelBtn.addActionListener(l -> this.dispose());

    addRow(serverTF, "Server URI:");
    addRow(userTF, "Username:");
    addRow(longTF, "Long polling interval:");
    addRow(shortTF, "Short polling interval:");

    SpringUtilities.makeCompactGrid(mainPane, 4, 2, // rows, cols
        6, 6, // initX, initY
        6, 6); // xPad, yPad

    JPanel buttonPanel = new JPanel(new BorderLayout());
    buttonPanel.add(okBtn, BorderLayout.EAST);
    buttonPanel.add(cancelBtn, BorderLayout.WEST);

    add(mainPane, BorderLayout.CENTER);
    add(buttonPanel, BorderLayout.SOUTH);

    setSize(300, 200);
    setVisible(true);
  }

  private void addRow(JTextField tf, String text) {
    JLabel l = new JLabel(text, JLabel.TRAILING);
    mainPane.add(l);
    l.setLabelFor(tf);
    mainPane.add(tf);
  }
}
