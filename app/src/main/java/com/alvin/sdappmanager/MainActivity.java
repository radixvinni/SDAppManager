package com.alvin.sdappmanager;

import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.Snackbar;
import android.view.Menu;
import android.view.View;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.widget.CheckedTextView;
import android.widget.ExpandableListView;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;


public class MainActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener {

    private Snackbar snackbar;
    private ApplicationListAdapter adapter;
    private ExpandableListView list;
    private ArrayList<Application> apps;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.setDrawerListener(toggle);
        toggle.syncState();

        NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);
        navigationView.getMenu().getItem(0).setChecked(true);

        final ArrayList<Application> selected = new ArrayList<Application>(); //why? 1.to hide Snackbar if no changes. 2. to quick check changed apps

        SharedPreferences settings = getSharedPreferences("Location", 0);
        final String sdcard = settings.getString("name","SD card");
        final String newpath = settings.getString("path","/sdcard");
        snackbar = Snackbar.make(drawer, "Move selected items to "+sdcard, Snackbar.LENGTH_INDEFINITE)
                .setAction("Apply changes", new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        List<String> commands = new ArrayList<String>();
                        for(Application app: selected) {
                            for(Dir dir: app.children) {
                                if (app.selection.contains(dir.name) && !app.selection_initial.contains(dir.name))
                                    commands.add(DirectoryData.getLinkCommand(dir.path, newpath, dir.owner));

                                if (!app.selection.contains(dir.name) && app.selection_initial.contains(dir.name))
                                    commands.add(DirectoryData.getUnlinkCommand(dir.path, dir.link, dir.owner));

                            }
                            app.selection_initial = new ArrayList<String>(app.selection);
                        }
                        final File lock = DirectoryData.runBatch(commands, newpath);
                        selected.clear();
                        if (lock==null) {
                            Toast.makeText(MainActivity.this,"Continuing in the background...",Toast.LENGTH_LONG).show();
                            return;
                        }
                        //TODO: save batch lock & check active locks on startup
                        final ProgressDialog pd = new ProgressDialog(MainActivity.this);
                        pd.setTitle("Moving files...");
                        pd.setMessage("Moving files to new locations");
                        pd.setIndeterminate(true);
                        pd.show();
                        pd.setOnCancelListener(new DialogInterface.OnCancelListener() {
                            @Override
                            public void onCancel(DialogInterface dialogInterface) {
                                Toast.makeText(MainActivity.this,"Continuing in the background...",Toast.LENGTH_LONG).show();
                            }
                        });
                        Handler h = new Handler() {
                            public void handleMessage(Message msg) {
                                if (lock.exists()) this.sendEmptyMessageDelayed(0, 1000);
                                else {
                                    pd.setIndeterminate(false);
                                    pd.dismiss();
                                    Toast.makeText(MainActivity.this, "Done!", Toast.LENGTH_LONG).show();

                                }
                            }
                        };
                        h.sendEmptyMessageDelayed(0, 1000);
                    }
                });

        list = (ExpandableListView)findViewById(R.id.list);
        apps = Application.getList(this);
        Collections.sort(apps);
        adapter = new ApplicationListAdapter(this, apps, list);
        list.setAdapter(adapter);
        list.setOnChildClickListener(new ExpandableListView.OnChildClickListener() {
            @Override
            public boolean onChildClick(ExpandableListView parent, View v,
                                        int groupPosition, int childPosition, long id) {
                CheckedTextView checkbox = (CheckedTextView) v.findViewById(R.id.list_item_text_child);
                checkbox.toggle();

                // find parent view by tag
                View parentView = list.findViewWithTag(apps.get(groupPosition).name);
                if (parentView != null) {
                    TextView sub = (TextView) parentView.findViewById(R.id.list_item_text_subscriptions);

                    if (sub != null) {
                        Application category = apps.get(groupPosition);
                        if (checkbox.isChecked()) {
                            // add child category to parent's selection list
                            category.selection.add(checkbox.getText().toString());

                            // sort list in alphabetical order
                            Collections.sort(category.selection);
                        } else
                            // remove child category from parent's selection list
                            category.selection.remove(checkbox.getText().toString());

                        if (category.selection.equals(category.selection_initial) && selected.contains(category))
                            selected.remove(category);
                        if (!category.selection.equals(category.selection_initial) && !selected.contains(category))
                            selected.add(category);

                        if (selected.isEmpty()) snackbar.dismiss();
                        if (!selected.isEmpty()) snackbar.show();
                        // display selection list
                        //sub.setText(category.children.get(0).link);
                    }
                }
                return true;
            }
        });

    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        //if (data == null) {return;}
        //String name = data.getStringExtra("name");
        SharedPreferences settings = getSharedPreferences("Location", 0);
        String path = settings.getString("path","");
        String name = settings.getString("name","SD card");
        snackbar.setText("Move selected items to "+name);
        ((TextView) findViewById(R.id.sd_name)).setText(name);
        ((TextView) findViewById(R.id.sd_descr)).setText(path);
        int[][] map = {
                {R.string.unknown_location, android.R.drawable.ic_menu_help},
                {R.string.system_partition, android.R.drawable.stat_sys_warning},
                {R.string.app_partition,    android.R.drawable.ic_popup_disk_full},
                {R.string.cache_partition,  android.R.drawable.stat_sys_warning},
                {R.string.sd_card,          android.R.drawable.stat_notify_sdcard},
                {R.string.ext_sd,           android.R.drawable.stat_notify_sdcard_prepare},
                {R.string.emulated,         android.R.drawable.stat_notify_sdcard_usb},
                {R.string.sd_usb,           android.R.drawable.stat_notify_sdcard_usb},
        };

        for (int[] v: map)
            if(name.equals(getString(v[0])))
                ((ImageView)findViewById(R.id.sd_icon)).setImageResource(v[1]);

    }

    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        // Handle navigation drawer item clicks
        int id = item.getItemId();

        if (id == R.id.nav_apps) {
        } else if (id == R.id.nav_locations) {
            Intent intent = new Intent(this, LocationActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivityForResult(intent, 1);
        } else if (id==R.id.nav_report)
            startActivity(new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://github.com/radixvinni/SDAppManager/issues")));

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

}
