package com.alvin.sdappmanager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageStats;
import android.content.pm.IPackageStatsObserver;
import android.graphics.drawable.Drawable;
import android.os.RemoteException;
import android.widget.Toast;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Semaphore;

abstract class ISizable implements Comparable {
    public long size;
    public String name;

    public String getSize() {
        long s = size;
        String suf = " b";
        if (s>1024) { s /= 1024; suf=" Kb";}
        if (s>1024) { s /= 1024; suf=" Mb";}
        if (s>1024) { s /= 1024; suf=" Gb";}
        return String.valueOf(s)+suf;
    }

    public String toString() {
        return this.name;
    }

    @Override
    public int compareTo(Object o) {
        return ((Long)((ISizable)o).size).compareTo(size);
    }
}

class Dir extends ISizable {
    public String path;
    public String owner;
    public String link;
    //public Application parent;

    public Dir(String n){name=n;}
    public Dir(long s){size=s;}

    public static void find_and_add_child(Application parent, String name, String path) {
        if (DirectoryData.instance.containsKey(path)) {
            Dir old = DirectoryData.instance.get(path);

            old.name = name;
            old.path = path;
            //old.parent = parent;

            if (old.link != null) parent.selection.add(name);
            parent.children.add(old);
        }
    }
}

public class Application extends ISizable{

    public ArrayList<Dir> children;
    public ArrayList<String> selection_initial;
    public ArrayList<String> selection;

    public String pack;
    public Drawable icon;
    public Boolean sys;


    public Application() {
        children = new ArrayList<Dir>();
        selection = new ArrayList<String>();
    }

    public Application(String name) {
        this();
        this.name = name;
    }

    public Application(ApplicationInfo applicationInfo, PackageManager pm, Context context) {
        this();
        name = applicationInfo.loadLabel(pm).toString();
        pack = applicationInfo.packageName;
        icon = applicationInfo.loadIcon(pm);
        sys = (applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
        Dir.find_and_add_child(this, context.getString(R.string.app_apk), applicationInfo.sourceDir);//publicSourceDir
        Dir.find_and_add_child(this, context.getString(R.string.app_data), applicationInfo.dataDir);
        Dir.find_and_add_child(this, context.getString(R.string.app_native), applicationInfo.nativeLibraryDir);

        for(Dir c:children) size+=c.size;
        Dir.find_and_add_child(this, context.getString(R.string.app_obb), DirectoryData.obb+pack);

        Collections.sort(children);
        selection_initial = new ArrayList<String>(selection);

    }


    /** Deprecated.
     * generate some random amount of child objects (1..10)
     */
    private void generateChildren() {
        Random rand = new Random();
        for(int i=0; i < rand.nextInt(9)+1; i++) {
            Dir cat = new Dir("Child "+i);
            this.children.add(cat);
        }
    }

    /** Deprecated.
     * generate some random objects (1..10)
     */
    public static ArrayList<Application> getRandom() {
        ArrayList<Application> apps = new ArrayList<Application>();
        for(int i = 0; i < 10 ; i++) {
            Application cat = new Application("Application "+i);
            cat.generateChildren();
            apps.add(cat);
        }
        return apps;
    }


    /** Deprecated.
     * Getting installed app size - wtf it does not work for me?
     * @browse http://stackoverflow.com/questions/1806286/getting-installed-app-size
     */

    public static ArrayList<Application> getList_(Context context) {
        final PackageManager packageManager = context.getPackageManager();
        ArrayList<Application> apps = new ArrayList<Application>();
        List<ApplicationInfo> installedApplications =
                packageManager.getInstalledApplications(PackageManager.GET_META_DATA);

        // Semaphore to handle concurrency
        final Semaphore codeSizeSemaphore = new Semaphore(1, true);

        // Code size will be here
        final long[] sizes = new long[8];

        for (ApplicationInfo appInfo : installedApplications)
        {
            try {codeSizeSemaphore.acquire();}
            catch (InterruptedException e) {e.printStackTrace(System.err);}

            try
            {
                Method getPackageSizeInfo =
                        packageManager.getClass().getMethod("getPackageSizeInfo",
                                String.class,
                                IPackageStatsObserver.class);

                getPackageSizeInfo.invoke(packageManager, appInfo.packageName,
                        new IPackageStatsObserver.Stub()
                        {
                            // Examples in the Internet usually have this method as @Override.
                            // I got an error with @Override. Perfectly works without it.
                            public void onGetStatsCompleted(PackageStats pStats, boolean succeedded)
                                    throws    RemoteException
                            {
                                sizes[0] = pStats.codeSize;
                                sizes[1] = pStats.dataSize;
                                sizes[2] = pStats.cacheSize;
                                sizes[3] = pStats.externalCodeSize;
                                sizes[4] = pStats.externalDataSize;
                                sizes[5] = pStats.externalCacheSize;
                                sizes[6] = pStats.externalObbSize;
                                sizes[7] = pStats.externalMediaSize;
                                codeSizeSemaphore.release();
                            }
                        });
            }
            catch (Exception e) {e.printStackTrace(System.err);}
            Application cat = new Application(appInfo, packageManager, context);
            //cat.size = sizes[0];
            cat.size = sizes[0]+sizes[1]+sizes[2]+sizes[3]+sizes[4]+sizes[5]+sizes[6]+sizes[7];
            apps.add(cat);
        }
        return apps;
    }
    public static ArrayList<Application> getList(Context context) {
        ArrayList<Application> apps = new ArrayList<Application>();

        final PackageManager pm = context.getPackageManager();
        List<ApplicationInfo> packages = pm.getInstalledApplications(PackageManager.GET_META_DATA);

        for (ApplicationInfo packageInfo : packages) {
            Application cat = new Application(packageInfo, pm, context);

            apps.add(cat);
        }
        return apps;
    }

}