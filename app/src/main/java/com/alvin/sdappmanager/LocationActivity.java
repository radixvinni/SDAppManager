package com.alvin.sdappmanager;
/*
 * Activity for screen "Locations"
 */
import android.content.SharedPreferences;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckedTextView;
import android.widget.ListView;

import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Set;

class Location implements Comparable {
    String name;
    String path;
    String device;
    Integer total;
    Integer used;
    Integer free;
    String fs;
    Boolean rw;

    Location(String _path) {
        path = _path;
    }

    public void edit(SharedPreferences.Editor e){
        e.putString("name", name);
        e.putString("path", path);
        e.putString("device", device);
        e.putInt("total", total);
        e.putInt("used", used);
        e.putInt("free", free);
        e.putString("fs", fs);
        e.putBoolean("rw", rw);
        e.commit();//TODO: Ok-Cancel thing
        //TODO: Mount? remount? write config.

    }

    @Override
    public int compareTo(Object o) {
        return ((Location)o).free.compareTo(free);
    }
}


class InteractiveArrayAdapter extends ArrayAdapter<Location> {
    private String selected = "";
    private final List<Location> list;
    private final Activity context;

    public InteractiveArrayAdapter(Activity context, List<Location> list) {
        super(context, R.layout.location_item, list);
        this.context = context;
        this.list = list;
        SharedPreferences settings = context.getSharedPreferences("Location", 0);
        selected = settings.getString("path",selected);
    }

    static class ViewHolder {
        protected TextView text;
        protected CheckedTextView checkbox;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view = null;
        if (convertView == null) {
            LayoutInflater inflator = context.getLayoutInflater();
            view = inflator.inflate(R.layout.location_item, null);
            final ViewHolder viewHolder = new ViewHolder();
            viewHolder.text = (TextView) view.findViewById(R.id.path);
            viewHolder.checkbox = (CheckedTextView) view.findViewById(R.id.name);

            view.setTag(viewHolder);
            viewHolder.checkbox.setTag(list.get(position));
        } else {
            view = convertView;
            ((ViewHolder) view.getTag()).checkbox.setTag(list.get(position));
        }
        ViewHolder holder = (ViewHolder) view.getTag();
        holder.text.setText(list.get(position).path);
        holder.checkbox.setText(list.get(position).name);
        holder.checkbox.setChecked(list.get(position).path.equals(selected));
        return view;
    }
    public void toggleChecked(int position) {
        selected = list.get(position).path;
        notifyDataSetChanged();

        SharedPreferences settings = context.getSharedPreferences("Location", 0);
        list.get(position).edit(settings.edit());

        //Toast.makeText(context, list.get(position).path, Toast.LENGTH_LONG).show();
    }

}


public class LocationActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_location);
        ListView list = (ListView)findViewById(R.id.listView);

        final InteractiveArrayAdapter adapter = new InteractiveArrayAdapter(this, getModel());
        //new ArrayAdapter<String>(this,R.layout.location_item, R.id.name, {"/mnt/sdcard1", "/mnt/sdcard2"});

        list.setAdapter(adapter);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                adapter.toggleChecked(i);
            }
        });

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        //Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        //setSupportActionBar(toolbar);
    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        //TODO: Make this screen a Ok-Cancel Dialog. Don't commit bad things like vfat locations.
        //getMenuInflater().inflate(R.menu.done, menu);
        return true;
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_menu_done:
            case android.R.id.home:
                //intent.putExtra("name", "");
                //setResult(RESULT_OK, intent);
                finish ();
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    private String getOutput(String[] cmd) {
        String s = "";
        try {
            final Process process = new ProcessBuilder().command(cmd)
                    .redirectErrorStream(true).start();
            process.waitFor();
            final InputStream is = process.getInputStream();
            final byte[] buffer = new byte[1024];
            while (is.read(buffer) != -1) {
                s = s + new String(buffer);
            }
            is.close();
        } catch (final Exception e) {
            e.printStackTrace();
        }
        return s;
    }
    private List<Location> getDf() {
        List<Location> list = new ArrayList<Location>();
        final String[] lines = getOutput(new String[]{"busybox","df"}).split("\n");
        final String mount = getOutput(new String[]{"mount"});
        Set<String> set = new HashSet<String>();
        for (String line : lines) {
            if (line == lines[0]) continue;
            String l = line.toLowerCase(Locale.US);
            String[] parts = line.split("\\s+");
            if (parts.length<6) continue;
            String dev = parts[0];
            if (Arrays.asList("tmpfs", "rootfs", "none", "sysfs", "devpts", "proc", "htcfs").contains(dev)) continue;

            String path = parts[5];
            if (path.startsWith("/sys")) continue;
            if (path.contains("/asec")) continue;
            if (path.contains("/secure")) continue;
            if (path.endsWith("/obb")) continue;
            Integer total, used, free;
            try {
             total = Integer.parseInt(parts[1]);
             used = Integer.parseInt(parts[2]);
             free = Integer.parseInt(parts[3]);
            }
            catch (NumberFormatException e) { continue; }
            if (free < 10000) continue;

            String name = "";
            switch(path) {
                case "/system":name=getString(R.string.system_partition);break;
                case "/data":name=getString(R.string.app_partition);break;
                case "/cache":name=getString(R.string.cache_partition);break;
            }
            if (path.contains("/ext")) name=getString(R.string.ext_sd);
            else if (path.contains("sdcard")) name=getString(R.string.sd_card);
            else if (path.contains("usb")) name=getString(R.string.sd_usb);

            if (path.contains("emulated")) name=getString(R.string.emulated);

            Location loc = new Location(path);
            loc.name=name.equals("")?getString(R.string.unknown_location):name;
            loc.device=dev;
            loc.free=free;
            loc.used=used;
            loc.total=total;

            int i = mount.indexOf(" "+path+" ");
            if(i != -1) {
                parts=mount.substring(i, mount.indexOf("\n",i)).split(" ");
                if (parts[1].equals("type")) {
                    loc.fs = parts[2];
                    loc.rw = parts[3].startsWith("rw") || parts[3].startsWith("(rw");
                }
                else {
                    loc.fs = parts[1];
                    loc.rw = parts[2].startsWith("rw") || parts[2].startsWith("(rw");
                }
            }
            if (!set.contains(path)) list.add(loc);
            set.add(path);
        }

        //list.get(0).setSelected(true);
        return list;
    }
    private List<Location> getModel() {
        List<Location> list = getDf();
        Collections.sort(list);
        //list.add(new Location(System.getenv("EXTERNAL_STORAGE")));
        //list.add(new Location(System.getenv("SECONDARY_STORAGE")));
        //list.add(new Location(System.getenv("EMULATED_STORAGE_TARGET")));
        return list;
    }


    }
