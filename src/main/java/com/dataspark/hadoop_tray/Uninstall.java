package com.dataspark.hadoop_tray;

import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

public class Uninstall {
  public static void main(String[] args) {
    try {
      Preferences.userRoot().node(Options.Pref_Key).removeNode();
      System.err.println("Preferences are succesfully removed.");
    } catch (BackingStoreException e) {
      System.err.println("Could not remove the saved preferences.");
      e.printStackTrace();
    }
  }
}
