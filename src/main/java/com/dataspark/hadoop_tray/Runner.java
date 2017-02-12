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
import java.awt.TrayIcon.MessageType;
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
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

public class Runner {
  public final static String Url = "http://ds-ga-nn:8088/cluster/apps/RUNNING";
  private static ScheduledExecutorService scheduler;
  private static int state = 0; // 0 not running, 1 there are running jobs
  // private static int numOfRunningJobs = 0; // my jobs
  private static List<JobInfo> myRunningJobs = emptyList();
  private static List<JobInfo> runningJobs = emptyList();

  private static int width = 300;
  private static int height = 50;
  private static double memoryTotal;
  private static double memoryUsed;
  private static TrayIcon trayIcon;
  private static double vCoresUsed;
  private static double vCoresTotal;
  private static String browseUrl = "http://ds-ga-nn:8088/cluster/app/";
  private static Color textColor;

  private static boolean shouldNotify = true;


  public static void main(String[] args) throws AWTException {

    // TrayIcon newTrayIcon = createTrayIcon();
    // trayIcon = newTrayIcon;
    // getSystemTray().add(trayIcon);

    // UIDefaults defaults = UIManager.getLookAndFeelDefaults();
    // System.out.println(defaults);
    // for (Object key : defaults.keySet()) {
    // System.out.println(key.toString() + ": " + defaults.get(key).toString());
    // }

    if (!SystemTray.isSupported()) {
      System.err.println("Sytem tray is not supported :(");
    }
    PopupMenu pm = new PopupMenu("Hadoop");
    addQuitTo(pm);

    Image image = newImageWithText(width, height, "Hello");
    trayIcon = new TrayIcon(image);
    trayIcon.setPopupMenu(pm);
    getSystemTray().add(trayIcon);
    trayIcon.displayMessage("Hello, World", "notification demo", MessageType.WARNING);

    scheduler = Executors.newScheduledThreadPool(1);

    checkJobs();
  }

  private static void checkJobs() {
    int delay = 60;
    // textColor = UIManager.getColor("MenuBar.highlight");
    // textColor = UIManager.getColor("MenuBar.foreground");
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
      System.out.println(jobs);

      List<JobInfo> myJobs =
          jobs.stream().filter(j -> j.user.equals("aksehirli")).collect(toList());
      int numOfMyJobs = myJobs.size();
      System.out.println(numOfMyJobs);

      if (numOfMyJobs > 0) {
        delay = 10;
        textColor = blue;
        if (state == 1 && myRunningJobs.size() == numOfMyJobs) {
          // System.out.println("DEBUG: No new jobs, returning.");
          scheduler.schedule(() -> checkJobs(), delay, SECONDS);
          // System.out.println("Schedule a new check with delay of " + delay);
          return;
        }

        if (myRunningJobs.size() > numOfMyJobs) {
          textColor = green;
          showNotification("Some jobs are finished!");
        }
        String jobsStr;
        if (numOfMyJobs == 1)
          jobsStr = "job";
        else
          jobsStr = "jobs";
        showNotification(
            String.format("You have %d " + jobsStr + " RUNNING on the system", numOfMyJobs));

        state = 1;

        myRunningJobs = myJobs;
        // numOfRunningJobs = numOfMyJobs;
      } else {
        if (state == 1) {
          shouldNotify = true;
          showNotification("All jobs are finished!");
        }
        state = 0;
        myRunningJobs.clear();
        delay = 60;
      }
    } catch (IOException e) {
      System.err.println("There is a problem with the connection: " + e.toString());
    } catch (ScriptException e) {
      System.err.println("Cannot parse the page: " + e.toString());
    }

    TrayIcon newTrayIcon = createTrayIcon();
    getSystemTray().remove(trayIcon);
    trayIcon = newTrayIcon;
    try {
      getSystemTray().add(trayIcon);
    } catch (AWTException e) {
      e.printStackTrace();
    }

    // TODO: Keep track of scheduled jobs
    scheduler.schedule(() -> checkJobs(), delay, SECONDS);
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
    Document doc = Jsoup.connect(Url).get();
    Elements metrics = doc.select("#metricsoverview tbody tr td");
    memoryUsed = parseDouble(metrics.get(5).text().split(" ")[0]);
    memoryTotal = parseDouble(metrics.get(6).text().split(" ")[0]);
    vCoresUsed = parseDouble(metrics.get(7).text().split(" ")[0]);
    vCoresTotal = parseDouble(metrics.get(8).text().split(" ")[0]);

    Elements table = doc.select("#apps");
    Elements tableScript = table.select("script");

    ScriptEngineManager engineManager = new ScriptEngineManager();
    ScriptEngine engine = engineManager.getEngineByName("nashorn");

    engine.eval(tableScript.html());
    // Long numOfJobs = (Long) engine.eval("appsTableData.length;");
    // System.out.println(numOfJobs);
    // System.out.println(arr);
    String[][] jarr = (String[][]) engine.eval("Java.to(appsTableData,'java.lang.String[][]')");

    List<JobInfo> jobs = Arrays.asList(jarr).stream().map(infoArr -> {
      int len = infoArr.length;
      return JobInfo.of(infoArr[0], infoArr[1], infoArr[2], parseInt(infoArr[len - 5]),
          parseInt(infoArr[len - 4]), parseInt(infoArr[len - 3]));
    }).collect(toList());
    return jobs;
  }

  private static Image newImageWithText(int width, int height, String str) {
    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = (Graphics2D) image.getGraphics();
    // System.out.println("writing with " + textColor);
    g.setColor(textColor);
    Font font = new Font("Arial", Font.PLAIN, (int) (height * .6));
    g.setFont(font);
    FontMetrics fontMetrics = g.getFontMetrics(font);
    int strHeight = fontMetrics.getHeight();
    int strWidth = fontMetrics.stringWidth(str);
    // g.drawString(str, 2, height - (height - strHeight) / 2);
    g.drawString(str, 2, (int) (height * 0.8));
    // System.out.println(strWidth);
    if (strWidth + 2 < width && strHeight < height) {
      return image.getSubimage(0, 0, strWidth + 2, height);
    }
    System.err.println("Oops, development code name is too large. Cropping.");
    return image;
  }

  private static TrayIcon createTrayIcon() {
    PopupMenu pm = new PopupMenu("Hadoop Monitor");
    pm.add(newDisabledMenuItem("-= Your jobs =-"));
    // pm.addSeparator();
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

  private static void addQuitTo(PopupMenu pm) {
    pm.addSeparator();
    MenuItem mi = new MenuItem("Quit");
    mi.addActionListener(l -> System.exit(0));
    pm.add(mi);
  }

  private static void createAndAddTo(PopupMenu pm, JobInfo j) {
    MenuItem mi = new MenuItem(j.user + ": " + j.name);
    mi.addActionListener(e -> openLink(browseUrl + j.id));
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
