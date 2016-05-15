package com.alvin.sdappmanager;
/*
 * This file contains all superuser commands-related stuff
 */
import android.os.Environment;

import java.io.DataOutputStream;
import java.io.File;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;

//TODO: list /data/dalvik-cache
//TODO: this singleton wants to be lazy or sqlite-cached
public class DirectoryData extends HashMap<String, Dir> {
    public static final DirectoryData instance = new DirectoryData();
    public static final String obb = Environment.getExternalStorageDirectory().getAbsolutePath() + "/Android/obb/";

    public DirectoryData() {
        super();
        //if (1==1) return;
        String s = "";
        try {
            Process p = Runtime.getRuntime().exec("su");
            DataOutputStream outputStream = new DataOutputStream(p.getOutputStream());
            outputStream.writeBytes("toolbox du -L -d 1 /data/data\n");
            outputStream.flush();//TODO: Note: du -L sums symlinks inside directory. The right way is to use: for i in /data/data/*;do du -s $i;done
            outputStream.writeBytes("toolbox du -L -d 1 /data/app-lib\n");
            outputStream.writeBytes("toolbox du -L -d 1 " + obb + "\n");
            outputStream.writeBytes("echo !done!\n");
            outputStream.writeBytes("toolbox ls -l /data/app\n");
            outputStream.writeBytes("echo !done!\n");
            outputStream.writeBytes("toolbox ls -l /data/data\n");
            outputStream.writeBytes("echo !done!\n");
            outputStream.writeBytes("toolbox ls -l /data/app-lib\n");
            outputStream.writeBytes("echo !done!\n");
            outputStream.writeBytes("toolbox ls -l " + obb + "\n");
            outputStream.writeBytes("exit\n");
            outputStream.flush();
            p.waitFor();
            final InputStream is = p.getInputStream();
            final byte[] buffer = new byte[1024];
            while (is.read(buffer) != -1) s += new String(buffer);

            int state = 0;
            for (String line : s.split("\n")) {
                if (line.equals("!done!")) state++;
                String[] parts = line.split("\\s+");
                Dir a;
                switch(state) {
                    case 0:
                        if (parts.length < 2) break;
                        try {
                            put(parts[1], new Dir(1024*Integer.parseInt(parts[0])));
                        } catch (NumberFormatException e) {}
                        break;
                    case 1:
                        if (line.charAt(0) == 'd') break;
                        if (parts.length < 7) break;

                        try {
                            a = new Dir(Integer.parseInt(parts[3]));
                        } catch (NumberFormatException e) {break;}

                        if (line.charAt(0) == 'l' && parts.length > 8) {
                            a.link = parts[8];
                        }
                        put("/data/app/" + parts[6], a);
                        break;
                    case 2:
                        if (line.charAt(0) == '-') break;
                        if (parts.length < 6 || parts.length > 8) break;
                        a = get("/data/data/" + parts[5]);
                        if (line.charAt(0) == 'l' && parts.length > 7)
                            a.link = parts[7];

                        a.owner = parts[1];
                        put("/data/data/" + parts[5], a);
                        break;
                    case 3:
                        if (line.charAt(0) == '-') break;
                        if (parts.length < 6 || parts.length > 8) break;
                        a = get("/data/app-lib/" + parts[5]);
                        if (line.charAt(0) == 'l' && parts.length > 7) a.link = parts[7];
                        put("/data/app-lib/" + parts[5], a);
                        break;
                    case 4:
                        if (line.charAt(0) == '-') break;
                        if (parts.length < 6 || parts.length > 8) break;
                        a = get(obb + parts[5]);
                        if (line.charAt(0) == 'l' && parts.length > 7) a.link = parts[7];
                        put(obb + parts[5], a);
                }
            }
            is.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static final String dir = File.separator + "mgr" + File.separator;

    public static String getLinkCommand(String from, String to, String owner) {
        if (owner==null) owner = "1000";
        else owner = String.valueOf(android.os.Process.getUidForName(owner));
        to += dir+(new File(from)).getName();
        //TODO: check from.exists, !to.exists, to.availableSpace() > from.size
        return "cp -rp "+from+" "+to+"\nrm -rf "+from+"\nln -s "+to+" "+from+
                "\nbusybox chown -h "+owner+":"+owner+" "+from+"\n";
    }

    public static String getUnlinkCommand(String from, String to, String owner) {
        if (owner==null) owner = "system";
        return "rm "+from+"\ncp -rp "+to+" "+from+"\n";
    }

    public static File runBatch(List<String> commands, String to) {
        try {
            File lock =  File.createTempFile(".mgr",".lock");
            Process p = Runtime.getRuntime().exec("su");
            DataOutputStream outputStream = new DataOutputStream(p.getOutputStream());
            outputStream.writeBytes("mkdir -p "+to+dir+"\n");
            for(String cmd: commands) outputStream.writeBytes(cmd);
            outputStream.writeBytes("rm "+lock.getAbsolutePath()+"\n");
            outputStream.flush();
            return lock;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
