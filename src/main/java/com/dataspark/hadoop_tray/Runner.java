package com.dataspark.hadoop_tray;

import static java.awt.Color.black;
import static java.awt.Color.blue;
import static java.awt.Color.green;
import static java.awt.Color.red;
import static java.awt.Color.white;
import static java.awt.SystemTray.getSystemTray;
import static java.lang.Double.parseDouble;
import static java.lang.Integer.parseInt;
import static java.util.Collections.emptyList;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.toList;
import static javax.swing.JOptionPane.INFORMATION_MESSAGE;
import static javax.swing.JOptionPane.showMessageDialog;

import java.awt.AWTException;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.prefs.BackingStoreException;
import java.util.prefs.NodeChangeEvent;
import java.util.prefs.NodeChangeListener;
import java.util.prefs.Preferences;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import javax.swing.JOptionPane;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

public class Runner {
  public final static String Running = "RUNNING";
  private static ScheduledExecutorService scheduler;
  private static int state = 0; // 0 not running, 1 there are running jobs
  private static List<JobInfo> myRunningJobs = emptyList();
  private static List<JobInfo> runningJobs = emptyList();

  private static int width = 300;
  private static int height = 50;
  private static double memoryTotal;
  private static double memoryUsed;
  private static TrayIcon trayIcon;
  private static double vCoresUsed;
  private static double vCoresTotal;
  private static Color textColor;

  private static boolean shouldNotify = false;
  private static ScheduledFuture<?> poller;

  private static Options options;

  private static int currentFrequency = 0;

  public static void main(String[] args) {
    options = new Options(() -> updateOptions());
    if (options.getUser().isEmpty()) {
      showMessageDialog(null,
          "<html>Hello <b>" + System.getProperty("user.name") + "</b>!\n"
              + "It looks like this is the first time you are using this application.\n"
              + "We need you some information from you.",
          "Welcome to Hadoop Monitor!", INFORMATION_MESSAGE);
      options.openDialog(() -> run());
    } else {
      run();
    }
  }

  private static void run() {
    if (!SystemTray.isSupported()) {
      showMessageDialog(null, "Sytem tray is not supported :( Exiting.", "Not supported",
          JOptionPane.ERROR_MESSAGE);
      System.err.println("Sytem tray is not supported :(");
      System.exit(1);
    }
    PopupMenu pm = new PopupMenu("Hadoop");
    addQuitTo(pm);

    trayIcon = new TrayIcon(newImageWithText(width, height, "Hello"));
    trayIcon.setPopupMenu(pm);
    try {
      getSystemTray().add(trayIcon);
    } catch (AWTException e) {
      showMessageDialog(null, "Cannot add the tray icon.");
      e.printStackTrace();
    }

    scheduler = Executors.newScheduledThreadPool(1);

    poller = scheduler.scheduleAtFixedRate(() -> checkJobs(), 0, 60, SECONDS);
  }

  private static void checkJobs() {
    if (shouldNotify) {
      textColor = red;
    } else if (isMacMenuBarDarkMode()) {
      textColor = white;
    } else {
      textColor = black;
    }
    try {
      List<JobInfo> jobs = fetchJobs();
      runningJobs = jobs;

      List<JobInfo> myJobs =
          jobs.stream().filter(j -> j.user.equals(options.getUser())).collect(toList());
      int numOfMyJobs = myJobs.size();

      if (numOfMyJobs > 0) {
        textColor = blue;

        if (state == 1 && myRunningJobs.size() == numOfMyJobs) {
          return;
        }

        if (myRunningJobs.size() > numOfMyJobs) {
          textColor = green;
          showNotification("Some jobs are finished!");
        }

        String jobsStr = numOfMyJobs == 1 ? "job" : "jobs";
        showNotification(
            String.format("You have %d " + jobsStr + " RUNNING on the system", numOfMyJobs));

        state = 1;

        myRunningJobs = myJobs;
      } else {
        if (state == 1) {
          shouldNotify = true;
          showNotification("All jobs are finished!");
        }
        state = 0;
        myRunningJobs.clear();
      }
    } catch (IOException e) {
      System.err.println("There is a problem with the connection: " + e.toString());
    } catch (ScriptException e) {
      System.err.println("Cannot parse the page: " + e.toString());
    }

    updateFrequency();
    TrayIcon newTrayIcon = createTrayIcon();
    getSystemTray().remove(trayIcon);
    trayIcon = newTrayIcon;
    try {
      getSystemTray().add(trayIcon);
    } catch (AWTException e) {
      e.printStackTrace();
    }
  }

  private static void updateFrequency() {
    if (myRunningJobs.isEmpty()) {
      changeFrequency(options.getNonTaskFreq());
    } else {
      changeFrequency(options.getTaskFreq());
    }
  }

  private static void changeFrequency(int period) {
    if (period == currentFrequency) {
      return;
    }
    System.out.println("Changing polling frequency to:" + period);
    currentFrequency = period;
    poller.cancel(false);
    poller = scheduler.scheduleAtFixedRate(() -> checkJobs(), 5, period, SECONDS);
  }

  private static void showNotification(String message) {
    // define your applescript command
    String command = "display notification \"" + message + "\" sound name \"Purr\" ";

    // run the command
    Runtime runtime = Runtime.getRuntime();
    String[] code = new String[] {"osascript", "-e", command};
    try {
      runtime.exec(code);
    } catch (IOException e) {
      System.err.println("Cannot show the notification: " + e.toString());
      System.err.println("The meesage was: " + message);
      e.printStackTrace();
    }
  }

  private static List<JobInfo> fetchJobs() throws IOException, ScriptException {
    Document doc = Jsoup.connect(options.getApps(Running)).get();
    Elements metrics = doc.select("#metricsoverview tbody tr td");
    memoryUsed = parseDouble(metrics.get(5).text().split(" ")[0]);
    memoryTotal = parseDouble(metrics.get(6).text().split(" ")[0]);
    vCoresUsed = parseDouble(metrics.get(8).text().split(" ")[0]);
    vCoresTotal = parseDouble(metrics.get(9).text().split(" ")[0]);

    Elements table = doc.select("#apps");
    Elements tableScript = table.select("script");

    ScriptEngineManager engineManager = new ScriptEngineManager();
    ScriptEngine engine = engineManager.getEngineByName("nashorn");

    engine.eval(tableScript.html());
    String[][] jarr = (String[][]) engine.eval("Java.to(appsTableData,'java.lang.String[][]')");

    List<JobInfo> jobs = Arrays.asList(jarr).stream().map(infoArr -> {
      int len = infoArr.length;
      String id = Jsoup.parseBodyFragment(infoArr[0]).select("a").html();
      return JobInfo.of(id, infoArr[1], infoArr[2], parseInt(infoArr[len - 5]),
          parseInt(infoArr[len - 4]), parseInt(infoArr[len - 3]));
    }).collect(toList());
    return jobs;
  }

  private static Image newImageWithText(int width, int height, String str) {
    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = (Graphics2D) image.getGraphics();
    g.setColor(textColor);
    Font font = new Font("Arial", Font.PLAIN, (int) (height * .6));
    g.setFont(font);
    FontMetrics fontMetrics = g.getFontMetrics(font);
    int strHeight = fontMetrics.getHeight();
    int strWidth = fontMetrics.stringWidth(str);
    g.drawString(str, 2, (int) (height * 0.8));
    if (strWidth + 2 < width && strHeight < height) {
      return image.getSubimage(0, 0, strWidth + 2, height);
    }
    System.err.println("Oops, development code name is too large. Cropping.");
    return image;
  }

  private static TrayIcon createTrayIcon() {
    PopupMenu pm = new PopupMenu("Hadoop Monitor");
    pm.add(newDisabledMenuItem("-= Your jobs =-"));
    if (myRunningJobs.isEmpty()) {
      pm.add(newDisabledMenuItem("All finished! ✓"));
    }
    myRunningJobs.forEach(j -> createAndAddTo(pm, j));
    pm.addSeparator();
    pm.add(newDisabledMenuItem("-= Others =-"));
    List<JobInfo> otherJobs = new ArrayList<>(runningJobs);
    otherJobs.removeAll(myRunningJobs);
    if (otherJobs.isEmpty()) {
      pm.add(newDisabledMenuItem("Server is idle. Quick, submit a job!"));
    }
    otherJobs.forEach(j -> createAndAddTo(pm, j));

    addRefreshTo(pm);
    addOptionsTo(pm);
    addQuitTo(pm);

    String text =
        String.format(".%d-.%d|%d", (int) (10 * memoryUsed / memoryTotal),
            (int) (10 * vCoresUsed / vCoresTotal), runningJobs.size());
    TrayIcon trayIcon =
        new TrayIcon(newImageWithText(width, height, text), "Click to see jobs", pm);

    trayIcon.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        removeNotification();
        checkJobs();
      };
    });
    return trayIcon;
  }

  private static boolean removeNotification() {
    System.err.println("Remove notification!");
    return shouldNotify = false;
  }

  private static void addRefreshTo(PopupMenu pm) {
    pm.addSeparator();
    MenuItem mi = new MenuItem("Refresh");
    mi.addActionListener(l -> checkJobs());
    pm.add(mi);
  }

  private static void addOptionsTo(PopupMenu pm) {
    pm.addSeparator();
    MenuItem mi = new MenuItem("Options");
    mi.addActionListener(l -> options.openDialog(() -> updateOptions()));
    pm.add(mi);
  }

  private static void addQuitTo(PopupMenu pm) {
    pm.addSeparator();
    MenuItem mi = new MenuItem("Quit");
    mi.addActionListener(l -> System.exit(0));
    pm.add(mi);
  }

  private static void createAndAddTo(PopupMenu pm, JobInfo j) {
    MenuItem mi = new MenuItem(j.user + ": (" + j.vcores + ") " + j.name);
    mi.addActionListener(e -> openLink(options.getApp(j.id)));
    pm.add(mi);
  }

  private static MenuItem newDisabledMenuItem(String label) {
    MenuItem mi = new MenuItem(label);
    mi.setEnabled(false);
    return mi;
  }

  private static void openLink(String uri) {
    try {
      openLink(new URI(uri));
    } catch (URISyntaxException e) {
      e.printStackTrace();
    }
  }

  private static void openLink(URI uri) {
    Desktop desktop = Desktop.isDesktopSupported() ? Desktop.getDesktop() : null;
    if (desktop != null && desktop.isSupported(Desktop.Action.BROWSE)) {
      try {
        desktop.browse(uri);
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
  }

  private static void updateOptions() {
    System.out.println("Options updated.");
    checkJobs();
  }

  public static class OptionsListener implements NodeChangeListener {
    @Override
    public void childAdded(NodeChangeEvent evt) {
      updateOptions();
    }

    @Override
    public void childRemoved(NodeChangeEvent evt) {
      updateOptions();
    }
  }

  @Value
  @AllArgsConstructor(staticName = "of")
  static class JobInfo {
    String id;
    String user;
    String name;
    int containers;
    int vcores;
    int memory;
  }

  /**
   * @return true if <code>defaults read -g AppleInterfaceStyle</code> has an exit status of
   *         <code>0</code> (i.e. _not_ returning "key not found").
   */
  private static boolean isMacMenuBarDarkMode() {
    try {
      // check for exit status only. Once there are more modes than "dark" and "default", we might
      // need to analyze string contents..
      final Process proc =
          Runtime.getRuntime().exec(new String[] {"defaults", "read", "-g", "AppleInterfaceStyle"});
      proc.waitFor(100, MILLISECONDS);
      return proc.exitValue() == 0;
    } catch (IOException | InterruptedException | IllegalThreadStateException ex) {
      // IllegalThreadStateException thrown by proc.exitValue(), if process didn't terminate
      System.err.println(
          "Could not determine, whether 'dark mode' is being used. Falling back to default (light) mode.");
      return false;
    }
  }
}
