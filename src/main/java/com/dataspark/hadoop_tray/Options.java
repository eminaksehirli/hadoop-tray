package com.dataspark.hadoop_tray;

import static java.lang.Integer.parseInt;

import java.util.prefs.Preferences;
import lombok.Getter;

@Getter
public class Options {

  static final String Pref_Key = "com.dataspark.hadoop_tray";
  static final String User_Key = "user";
  static final String Server_Key = "server";
  static final String Non_Task_Freq_Key = "nonTaskFreq";
  static final String Task_Freq_Key = "taskFreq";

  public static String appsPath = "/cluster/apps";
  public static String appPath = "/cluster/app";

  public static String serverUrl = "http://ds-ga-nn:8088";
  public static int def_NonTaskFreq = 60;
  public static int def_TaskFreq = 10;

  private String user;
  private String server;
  private int nonTaskFreq;
  private int taskFreq;
  private CallBack changeListener;

  public Options(CallBack changeListener) {
    Preferences pref = Preferences.userRoot().node(Pref_Key);
    user = pref.get(User_Key, "");
    server = pref.get(Server_Key, serverUrl);
    nonTaskFreq = pref.getInt(Non_Task_Freq_Key, def_NonTaskFreq);
    taskFreq = pref.getInt(Task_Freq_Key, def_TaskFreq);

    this.changeListener = changeListener;
  }

  public void openDialog(CallBack callBack) {
    OptionsPane pane = new OptionsPane(server, user, nonTaskFreq, taskFreq);
    pane.show(l -> {
      getValuesFrom(pane);
      save();
      pane.dispose();
      callBack.call();
    });
  }

  private void save() {
    Preferences pref = Preferences.userRoot().node(Pref_Key);
    pref.put(User_Key, user);
    pref.put(Server_Key, server);
    pref.putInt(Non_Task_Freq_Key, nonTaskFreq);
    pref.putInt(Task_Freq_Key, taskFreq);
  }

  private void getValuesFrom(OptionsPane pane) {
    this.user = pane.userTF.getText();
    this.nonTaskFreq = parseInt(pane.longTF.getText());
    this.taskFreq = parseInt(pane.shortTF.getText());
    String serv = pane.serverTF.getText();
    if (serv.endsWith("/")) {
      serv = serv.substring(0, serv.length() - 1);
    }
    if (serv.endsWith(appsPath)) {
      serv = serv.substring(0, serv.length() - appsPath.length());
    }
    this.server = serv;
  }

  public static interface CallBack {
    void call();
  }

  public String getApps(String id) {
    return server + appsPath + "/" + id;
  }

  public String getApp(String id) {
    return server + appPath + "/" + id;
  }
}
