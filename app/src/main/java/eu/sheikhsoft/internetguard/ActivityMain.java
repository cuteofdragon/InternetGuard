package eu.sheikhsoft.internetguard;

/**
 * Created by Sk Shamimul islam on 12/13/2017.
 */


import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.databinding.DataBindingUtil;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.net.VpnService;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.design.widget.TabLayout;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.support.v4.view.ViewPager;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.app.AlertDialog;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SearchView;
import android.support.v7.widget.SwitchCompat;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.ImageSpan;
import android.text.style.UnderlineSpan;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.MobileAds;
import com.google.firebase.analytics.FirebaseAnalytics;

import java.util.List;

import eu.sheikhsoft.internetguard.Settings.SectionsPagerAdepter;
import eu.sheikhsoft.internetguard.databinding.MainBinding;

import static android.graphics.Color.TRANSPARENT;
import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;

public class ActivityMain extends AppCompatActivity implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String TAG = "InterNetGuard.Main";

    private boolean running = false;
    private ImageView ivIcon;
    private ImageView ivQueue;
    private SwitchCompat swEnabled;
    private ImageView ivMetered;
    private SwipeRefreshLayout swipeRefresh;
    private AdapterRule adapter = null;
    private MenuItem menuSearch = null;
    private AlertDialog dialogFirst = null;
    private AlertDialog dialogVpn = null;
    private AlertDialog dialogDoze = null;
    private AlertDialog dialogLegend = null;
    private AlertDialog dialogAbout = null;

    private IAB iab = null;

    private static final int REQUEST_VPN = 1;
    private static final int REQUEST_INVITE = 2;
    private static final int REQUEST_LOGCAT = 3;
    public static final int REQUEST_ROAMING = 4;

    private static final int MIN_SDK = Build.VERSION_CODES.LOLLIPOP_MR1;

    public static final String ACTION_RULES_CHANGED = "eu.sheikhsoft.internetguard.ACTION_RULES_CHANGED";
    public static final String ACTION_QUEUE_CHANGED = "eu.sheikhsoft.internetguard.ACTION_QUEUE_CHANGED";
    public static final String EXTRA_REFRESH = "Refresh";
    public static final String EXTRA_SEARCH = "Search";
    public static final String EXTRA_RELATED = "Related";
    public static final String EXTRA_APPROVE = "Approve";
    public static final String EXTRA_LOGCAT = "Logcat";
    public static final String EXTRA_CONNECTED = "Connected";
    public static final String EXTRA_METERED = "Metered";
    public static final String EXTRA_SIZE = "Size";

    //fab variable
    private static final String LOG_TAG = "MainActivity";

    private DisplayMetrics mDisplayMetrics;
    private MainBinding mBinding;
    private float mStartX;
    private float mStartY;
    private int mBottomY;
    private int mBottomX;

    private boolean mIsCancel;
    private float mBottomListStartY;
    private boolean resetBottomList;
    private AdView mAdView;
    private ViewPager mViewPager;
    private SectionsPagerAdepter mSectionsPagerAdapter;

    private TabLayout mTabLayout;
    private FirebaseAnalytics mFirebaseAnalytics;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "Create version=" + Util.getSelfVersionName(this) + "/" + Util.getSelfVersionCode(this));
        Util.logExtras(getIntent());

        // Check minimum Android version
        if (Build.VERSION.SDK_INT < MIN_SDK) {
            Log.i(TAG, "SDK=" + Build.VERSION.SDK_INT);
            super.onCreate(savedInstanceState);
            setContentView(R.layout.android);
            return;
        }

        // Check for Xposed
        if (Util.hasXposed(this)) {
            Log.i(TAG, "Xposed running");
            super.onCreate(savedInstanceState);
            setContentView(R.layout.xposed);
            return;
        }

        Util.setTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        mBinding = DataBindingUtil.setContentView(this, R.layout.main);

        running = true;

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean enabled = prefs.getBoolean("enabled", false);
        boolean initialized = prefs.getBoolean("initialized", false);

        // Upgrade
        ReceiverAutostart.upgrade(initialized, this);

        if (!getIntent().hasExtra(EXTRA_APPROVE)) {
            if (enabled)
                ServiceSinkhole.start("UI", this);
            else
                ServiceSinkhole.stop("UI", this, false);
        }

        // Action bar
        final View actionView = getLayoutInflater().inflate(R.layout.actionmain, null, false);
        ivIcon = actionView.findViewById(R.id.ivIcon);
        ivQueue = actionView.findViewById(R.id.ivQueue);
        swEnabled = actionView.findViewById(R.id.swEnabled);
        ivMetered = actionView.findViewById(R.id.ivMetered);

        // Icon
        ivIcon.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                menu_about();
                return true;
            }
        });

        // Title
        getSupportActionBar().setTitle(null);

        // Netguard is busy
        ivQueue.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                int location[] = new int[2];
                actionView.getLocationOnScreen(location);
                Toast toast = Toast.makeText(ActivityMain.this, R.string.msg_queue, Toast.LENGTH_LONG);
                toast.setGravity(
                        Gravity.TOP | Gravity.LEFT,
                        location[0] + ivQueue.getLeft(),
                        Math.round(location[1] + ivQueue.getBottom() - toast.getView().getPaddingTop()));
                toast.show();
                return true;
            }
        });

        // On/off switch
        swEnabled.setChecked(enabled);
        swEnabled.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                Log.i(TAG, "Switch=" + isChecked);
                prefs.edit().putBoolean("enabled", isChecked).apply();

                if (isChecked) {
                    String alwaysOn = Settings.Secure.getString(getContentResolver(), "always_on_vpn_app");
                    Log.i(TAG, "Always-on=" + alwaysOn);
                    if (!TextUtils.isEmpty(alwaysOn) && !getPackageName().equals(alwaysOn)) {
                        swEnabled.setChecked(false);
                        Toast.makeText(ActivityMain.this, R.string.msg_always_on, Toast.LENGTH_LONG).show();
                        return;
                    }

                    try {
                        final Intent prepare = VpnService.prepare(ActivityMain.this);
                        if (prepare == null) {
                            Log.i(TAG, "Prepare done");
                            onActivityResult(REQUEST_VPN, RESULT_OK, null);
                        } else {
                            // Show dialog
                            LayoutInflater inflater = LayoutInflater.from(ActivityMain.this);
                            View view = inflater.inflate(R.layout.vpn, null, false);
                            dialogVpn = new AlertDialog.Builder(ActivityMain.this)
                                    .setView(view)
                                    .setCancelable(false)
                                    .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            if (running) {
                                                Log.i(TAG, "Start intent=" + prepare);
                                                try {
                                                    // com.android.vpndialogs.ConfirmDialog required
                                                    startActivityForResult(prepare, REQUEST_VPN);
                                                } catch (Throwable ex) {
                                                    Log.e(TAG, ex.toString() + "\n" + Log.getStackTraceString(ex));
                                                    onActivityResult(REQUEST_VPN, RESULT_CANCELED, null);
                                                    prefs.edit().putBoolean("enabled", false).apply();
                                                }
                                            }
                                        }
                                    })
                                    .setOnDismissListener(new DialogInterface.OnDismissListener() {
                                        @Override
                                        public void onDismiss(DialogInterface dialogInterface) {
                                            dialogVpn = null;
                                        }
                                    })
                                    .create();
                            dialogVpn.show();
                        }
                    } catch (Throwable ex) {
                        // Prepare failed
                        Log.e(TAG, ex.toString() + "\n" + Log.getStackTraceString(ex));
                        prefs.edit().putBoolean("enabled", false).apply();
                    }

                } else
                    ServiceSinkhole.stop("switch off", ActivityMain.this, false);
            }
        });


        // Network is metered
        ivMetered.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                int location[] = new int[2];
                actionView.getLocationOnScreen(location);
                Toast toast = Toast.makeText(ActivityMain.this, R.string.msg_metered, Toast.LENGTH_LONG);
                toast.setGravity(
                        Gravity.TOP | Gravity.START,
                        location[0] + ivMetered.getLeft(),
                        Math.round(location[1] + ivMetered.getBottom() - toast.getView().getPaddingTop()));
                toast.show();
                return true;
            }
        });

        getSupportActionBar().setDisplayShowCustomEnabled(true);
        getSupportActionBar().setCustomView(actionView);

        // Disabled warning
        TextView tvDisabled = findViewById(R.id.tvDisabled);
        tvDisabled.setVisibility(enabled ? View.GONE : View.VISIBLE);

        // Application list
        RecyclerView rvApplication = findViewById(R.id.rvApplication);
        rvApplication.setHasFixedSize(true);
        rvApplication.setLayoutManager(new LinearLayoutManager(this));
        adapter = new AdapterRule(this);
        rvApplication.setAdapter(adapter);

        // Swipe to refresh
        TypedValue tv = new TypedValue();
        getTheme().resolveAttribute(R.attr.colorPrimary, tv, true);
        swipeRefresh = findViewById(R.id.swipeRefresh);
        swipeRefresh.setColorSchemeColors(Color.WHITE, Color.WHITE, Color.WHITE);
        swipeRefresh.setProgressBackgroundColorSchemeColor(tv.data);
        swipeRefresh.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                Rule.clearCache(ActivityMain.this);
                ServiceSinkhole.reload("pull", ActivityMain.this, false);
                updateApplicationList(null);
            }
        });

        // Hint usage
        final LinearLayout llUsage = findViewById(R.id.llUsage);
        Button btnUsage = findViewById(R.id.btnUsage);
        boolean hintUsage = prefs.getBoolean("hint_usage", true);
        llUsage.setVisibility(hintUsage ? View.VISIBLE : View.GONE);
        btnUsage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                prefs.edit().putBoolean("hint_usage", false).apply();
                llUsage.setVisibility(View.GONE);
                showHints();
            }
        });
        showHints();

        // Listen for preference changes
        prefs.registerOnSharedPreferenceChangeListener(this);

        // Listen for rule set changes
        IntentFilter ifr = new IntentFilter(ACTION_RULES_CHANGED);
        LocalBroadcastManager.getInstance(this).registerReceiver(onRulesChanged, ifr);

        // Listen for queue changes
        IntentFilter ifq = new IntentFilter(ACTION_QUEUE_CHANGED);
        LocalBroadcastManager.getInstance(this).registerReceiver(onQueueChanged, ifq);

        // Listen for added/removed applications
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Intent.ACTION_PACKAGE_ADDED);
        intentFilter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        intentFilter.addDataScheme("package");
        registerReceiver(packageChangedReceiver, intentFilter);

        // First use
        boolean admob = prefs.getBoolean("admob", false);
        if (!initialized || !admob) {
            // Create view
            LayoutInflater inflater = LayoutInflater.from(this);
            View view = inflater.inflate(R.layout.first, null, false);

            TextView tvFirst = view.findViewById(R.id.tvFirst);
            TextView tvPrivacy = view.findViewById(R.id.tvPrivacy);
            TextView tvAdmob = view.findViewById(R.id.tvAdmob);
            tvFirst.setMovementMethod(LinkMovementMethod.getInstance());
            tvPrivacy.setMovementMethod(LinkMovementMethod.getInstance());
         //   tvAdmob.setMovementMethod(LinkMovementMethod.getInstance());

            // Show dialog
            dialogFirst = new AlertDialog.Builder(this)
                    .setView(view)
                    .setCancelable(false)
                    .setPositiveButton(R.string.app_agree, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            if (running) {
                                prefs.edit().putBoolean("initialized", true).apply();
                                prefs.edit().putBoolean("admob", true).apply();
                            }
                        }
                    })
                    .setNegativeButton(R.string.app_disagree, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            if (running)
                                finish();
                        }
                    })
                    .setOnDismissListener(new DialogInterface.OnDismissListener() {
                        @Override
                        public void onDismiss(DialogInterface dialogInterface) {
                            dialogFirst = null;
                        }
                    })
                    .create();
            dialogFirst.show();
        }

        // Fill application list
        updateApplicationList(getIntent().getStringExtra(EXTRA_SEARCH));

        // Update IAB SKUs
       /** try {
            iab = new IAB(new IAB.Delegate() {
                @Override
                public void onReady(IAB iab) {
                    try {
                        iab.updatePurchases();

                        if (!IAB.isPurchased(ActivityPro.SKU_LOG, ActivityMain.this))
                            prefs.edit().putBoolean("log", false).apply();
                        if (!IAB.isPurchased(ActivityPro.SKU_THEME, ActivityMain.this)) {
                            if (!"teal".equals(prefs.getString("theme", "teal")))
                                prefs.edit().putString("theme", "teal").apply();
                        }
                        if (!IAB.isPurchased(ActivityPro.SKU_NOTIFY, ActivityMain.this))
                            prefs.edit().putBoolean("install", false).apply();
                        if (!IAB.isPurchased(ActivityPro.SKU_SPEED, ActivityMain.this))
                            prefs.edit().putBoolean("show_stats", false).apply();
                    } catch (Throwable ex) {
                        Log.e(TAG, ex.toString() + "\n" + Log.getStackTraceString(ex));
                    } finally {
                        iab.unbind();
                    }
                }
            }, this);
            iab.bind();
        } catch (Throwable ex) {
            Log.e(TAG, ex.toString() + "\n" + Log.getStackTraceString(ex));
        }
        **/

        // Initialize ads

        initAds();
        mFirebaseAnalytics = FirebaseAnalytics.getInstance(this);

        // Handle intent
        checkExtras(getIntent());


        // fab action
        setDisplayMetrics();

        mBinding.fab.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.sheet)));

        Drawable d = mBinding.bottomListBackground.getBackground();
        final GradientDrawable gd = (GradientDrawable) d;

        gd.setCornerRadius(0f);
        SetupFracments();

        mBinding.cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                menu_about();
            }
        });


    }

    private void SetupFracments() {
        mViewPager = findViewById(R.id.main_tab_pager);

        mSectionsPagerAdapter = new SectionsPagerAdepter(getFragmentManager());

        mViewPager.setAdapter(mSectionsPagerAdapter);

        mTabLayout = findViewById(R.id.main_tabs);

        mTabLayout.setupWithViewPager(mViewPager);
    }



    @Override
    protected void onNewIntent(Intent intent) {
        Log.i(TAG, "New intent");
        Util.logExtras(intent);
        super.onNewIntent(intent);

        if (Build.VERSION.SDK_INT < MIN_SDK || Util.hasXposed(this))
            return;

        setIntent(intent);

        if (Build.VERSION.SDK_INT >= MIN_SDK) {
            if (intent.hasExtra(EXTRA_REFRESH))
                updateApplicationList(intent.getStringExtra(EXTRA_SEARCH));
            else
                updateSearch(intent.getStringExtra(EXTRA_SEARCH));
            checkExtras(intent);
        }
    }

    @Override
    protected void onResume() {
        Log.i(TAG, "Resume");

        if (Build.VERSION.SDK_INT < MIN_SDK || Util.hasXposed(this)) {
            super.onResume();
            return;
        }

        DatabaseHelper.getInstance(this).addAccessChangedListener(accessChangedListener);
        if (adapter != null)
            adapter.notifyDataSetChanged();




        super.onResume();
    }

    @Override
    protected void onPause() {
        Log.i(TAG, "Pause");
        super.onPause();

        if (Build.VERSION.SDK_INT < MIN_SDK || Util.hasXposed(this))
            return;

        DatabaseHelper.getInstance(this).removeAccessChangedListener(accessChangedListener);

       // disableAds();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        Log.i(TAG, "Config");
        super.onConfigurationChanged(newConfig);

        if (Build.VERSION.SDK_INT < MIN_SDK || Util.hasXposed(this))
            return;



    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "Destroy");

        if (Build.VERSION.SDK_INT < MIN_SDK || Util.hasXposed(this)) {
            super.onDestroy();
            return;
        }

        running = false;
        adapter = null;

        PreferenceManager.getDefaultSharedPreferences(this).unregisterOnSharedPreferenceChangeListener(this);

        LocalBroadcastManager.getInstance(this).unregisterReceiver(onRulesChanged);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(onQueueChanged);
        unregisterReceiver(packageChangedReceiver);

        if (dialogFirst != null) {
            dialogFirst.dismiss();
            dialogFirst = null;
        }
        if (dialogVpn != null) {
            dialogVpn.dismiss();
            dialogVpn = null;
        }
        if (dialogDoze != null) {
            dialogDoze.dismiss();
            dialogDoze = null;
        }
        if (dialogLegend != null) {
            dialogLegend.dismiss();
            dialogLegend = null;
        }
        if (dialogAbout != null) {
            dialogAbout.dismiss();
            dialogAbout = null;
        }

        if (iab != null) {
            iab.unbind();
            iab = null;
        }

        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, final Intent data) {
        Log.i(TAG, "onActivityResult request=" + requestCode + " result=" + requestCode + " ok=" + (resultCode == RESULT_OK));
        Util.logExtras(data);

        if (requestCode == REQUEST_VPN) {
            // Handle VPN approval
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            prefs.edit().putBoolean("enabled", resultCode == RESULT_OK).apply();
            if (resultCode == RESULT_OK) {
                ServiceSinkhole.start("prepared", this);

                Toast on = Toast.makeText(ActivityMain.this, R.string.msg_on, Toast.LENGTH_LONG);
                on.setGravity(Gravity.CENTER, 0, 0);
                on.show();

                checkDoze();
            } else if (resultCode == RESULT_CANCELED)
                Toast.makeText(this, R.string.msg_vpn_cancelled, Toast.LENGTH_LONG).show();

        } else if (requestCode == REQUEST_INVITE) {
            // Do nothing

        } else if (requestCode == REQUEST_LOGCAT) {
            // Send logcat by e-mail
            if (resultCode == RESULT_OK) {
                Uri target = data.getData();
                if (data.hasExtra("org.openintents.extra.DIR_PATH"))
                    target = Uri.parse(target + "/logcat.txt");
                Log.i(TAG, "Export URI=" + target);
               // Util.sendLogcat(target, this);
            }

        } else {
            Log.w(TAG, "Unknown activity result request=" + requestCode);
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_ROAMING)
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED)
                ServiceSinkhole.reload("permission granted", this, false);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String name) {
        Log.i(TAG, "Preference " + name + "=" + prefs.getAll().get(name));
        if ("enabled".equals(name)) {
            // Get enabled
            boolean enabled = prefs.getBoolean(name, false);

            // Display disabled warning
            TextView tvDisabled = findViewById(R.id.tvDisabled);
            tvDisabled.setVisibility(enabled ? View.GONE : View.VISIBLE);

            // Check switch state
            SwitchCompat swEnabled = getSupportActionBar().getCustomView().findViewById(R.id.swEnabled);
            if (swEnabled.isChecked() != enabled)
                swEnabled.setChecked(enabled);

        } else if ("whitelist_wifi".equals(name) ||
                "screen_on".equals(name) ||
                "screen_wifi".equals(name) ||
                "whitelist_other".equals(name) ||
                "screen_other".equals(name) ||
                "whitelist_roaming".equals(name) ||
                "show_user".equals(name) ||
                "show_system".equals(name) ||
                "show_nointernet".equals(name) ||
                "show_disabled".equals(name) ||
                "sort".equals(name) ||
                "imported".equals(name)) {
            ServiceSinkhole.reload("changed " + name, this, false);
            updateApplicationList(null);

            final LinearLayout llWhitelist = findViewById(R.id.llWhitelist);
            boolean screen_on = prefs.getBoolean("screen_on", true);
            boolean whitelist_wifi = prefs.getBoolean("whitelist_wifi", false);
            boolean whitelist_other = prefs.getBoolean("whitelist_other", false);
            boolean hintWhitelist = prefs.getBoolean("hint_whitelist", true);
            llWhitelist.setVisibility(!(whitelist_wifi || whitelist_other) && screen_on && hintWhitelist ? View.VISIBLE : View.GONE);

        } else if ("manage_system".equals(name)) {
            invalidateOptionsMenu();
            updateApplicationList(null);

            LinearLayout llSystem = findViewById(R.id.llSystem);
            boolean system = prefs.getBoolean("manage_system", false);
            boolean hint = prefs.getBoolean("hint_system", true);
            llSystem.setVisibility(!system && hint ? View.VISIBLE : View.GONE);

        } else if ("theme".equals(name) || "dark_theme".equals(name)){
            recreate();
        }else if ("block_porn".equals(name)){
            boolean block_porn = prefs.getBoolean("block_porn", true);
            if (block_porn)
            {
                prefs.edit().putBoolean("filter", true).apply();
                prefs.edit().putString("dns","208.67.222.123").apply();
                prefs.edit().putString("dns2","208.67.220.123").apply();
                ServiceSinkhole.reload("changed " + name, this, false);
                Toast.makeText(this, "Block Porn Successful", Toast.LENGTH_SHORT).show();

            }else {
                prefs.edit().putBoolean("filter", false).apply();
                prefs.edit().putString("dns","8.8.8.8").apply();
                prefs.edit().putString("dns2","8.8.4.4").apply();
                ServiceSinkhole.reload("changed " + name, this, false);
                Toast.makeText(this, "You Enable Porn Website.Adult Videos are harmful for your health", Toast.LENGTH_SHORT).show();
            }

        }else if("show_stats".equals(name)){
            ServiceSinkhole.reloadStats("changed " + name, this);
        }

    }

    private DatabaseHelper.AccessChangedListener accessChangedListener = new DatabaseHelper.AccessChangedListener() {
        @Override
        public void onChanged() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (adapter != null && adapter.isLive())
                        adapter.notifyDataSetChanged();
                }
            });
        }
    };

    private BroadcastReceiver onRulesChanged = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.i(TAG, "Received " + intent);
            Util.logExtras(intent);

            if (adapter != null)
                if (intent.hasExtra(EXTRA_CONNECTED) && intent.hasExtra(EXTRA_METERED)) {
                    ivIcon.setImageResource(Util.isNetworkActive(ActivityMain.this)
                            ? R.drawable.ic_security_white_24dp
                            : R.drawable.ic_security_white_24dp_60);
                    if (intent.getBooleanExtra(EXTRA_CONNECTED, false)) {
                        if (intent.getBooleanExtra(EXTRA_METERED, false))
                            adapter.setMobileActive();
                        else
                            adapter.setWifiActive();
                        ivMetered.setVisibility(Util.isMeteredNetwork(ActivityMain.this) ? View.VISIBLE : View.INVISIBLE);
                    } else {
                        adapter.setDisconnected();
                        ivMetered.setVisibility(View.INVISIBLE);
                    }
                } else
                    updateApplicationList(null);
        }
    };

    private BroadcastReceiver onQueueChanged = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.i(TAG, "Received " + intent);
            Util.logExtras(intent);
            int size = intent.getIntExtra(EXTRA_SIZE, -1);
            ivIcon.setVisibility(size == 0 ? View.VISIBLE : View.GONE);
            ivQueue.setVisibility(size == 0 ? View.GONE : View.VISIBLE);
        }
    };

    private BroadcastReceiver packageChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.i(TAG, "Received " + intent);
            Util.logExtras(intent);
            updateApplicationList(null);
        }
    };

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (Build.VERSION.SDK_INT < MIN_SDK)
            return false;

        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main, menu);

        // Search
        menuSearch = menu.findItem(R.id.menu_search);
        menuSearch.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionExpand(MenuItem item) {
                return true;
            }

            @Override
            public boolean onMenuItemActionCollapse(MenuItem item) {
                if (getIntent().hasExtra(EXTRA_SEARCH) && !getIntent().getBooleanExtra(EXTRA_RELATED, false))
                    finish();
                return true;
            }
        });

        final SearchView searchView = (SearchView) menuSearch.getActionView();
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                if (adapter != null)
                    adapter.getFilter().filter(query);
                searchView.clearFocus();
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                if (adapter != null)
                    adapter.getFilter().filter(newText);
                return true;
            }
        });
        searchView.setOnCloseListener(new SearchView.OnCloseListener() {
            @Override
            public boolean onClose() {
                Intent intent = getIntent();
                intent.removeExtra(EXTRA_SEARCH);

                if (adapter != null)
                    adapter.getFilter().filter(null);
                return true;
            }
        });
        String search = getIntent().getStringExtra(EXTRA_SEARCH);
        if (search != null) {
            menuSearch.expandActionView();
            searchView.setQuery(search, true);
        }



        if (!Util.hasValidFingerprint(this) || getIntentInvite(this).resolveActivity(getPackageManager()) == null)
            menu.removeItem(R.id.menu_invite);

        if (getIntentSupport().resolveActivity(getPackageManager()) == null)
            menu.removeItem(R.id.menu_support);

        return true;
    }

    private void markPro(MenuItem menu, String sku) {
        if (sku == null || !IAB.isPurchased(sku, this)) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            boolean dark = prefs.getBoolean("dark_theme", false);
            SpannableStringBuilder ssb = new SpannableStringBuilder("  " + menu.getTitle());
            ssb.setSpan(new ImageSpan(this, dark ? R.drawable.ic_shopping_cart_white_24dp : R.drawable.ic_shopping_cart_black_24dp), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            menu.setTitle(ssb);
        }
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        if (prefs.getBoolean("manage_system", false)) {
            menu.findItem(R.id.menu_app_user).setChecked(prefs.getBoolean("show_user", true));
            menu.findItem(R.id.menu_app_system).setChecked(prefs.getBoolean("show_system", false));
        } else {
            Menu submenu = menu.findItem(R.id.menu_filter).getSubMenu();
            submenu.removeItem(R.id.menu_app_user);
            submenu.removeItem(R.id.menu_app_system);
        }

        menu.findItem(R.id.menu_app_nointernet).setChecked(prefs.getBoolean("show_nointernet", true));
        menu.findItem(R.id.menu_app_disabled).setChecked(prefs.getBoolean("show_disabled", true));

        String sort = prefs.getString("sort", "name");
        if ("uid".equals(sort))
            menu.findItem(R.id.menu_sort_uid).setChecked(true);
        else
            menu.findItem(R.id.menu_sort_name).setChecked(true);

        menu.findItem(R.id.menu_lockdown).setChecked(prefs.getBoolean("lockdown", false));

        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Log.i(TAG, "Menu=" + item.getTitle());

        // Handle item selection
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        switch (item.getItemId()) {
            case R.id.menu_app_user:
                item.setChecked(!item.isChecked());
                prefs.edit().putBoolean("show_user", item.isChecked()).apply();
                return true;

            case R.id.menu_app_system:
                item.setChecked(!item.isChecked());
                prefs.edit().putBoolean("show_system", item.isChecked()).apply();
                return true;

            case R.id.menu_app_nointernet:
                item.setChecked(!item.isChecked());
                prefs.edit().putBoolean("show_nointernet", item.isChecked()).apply();
                return true;

            case R.id.menu_app_disabled:
                item.setChecked(!item.isChecked());
                prefs.edit().putBoolean("show_disabled", item.isChecked()).apply();
                return true;

            case R.id.menu_sort_name:
                item.setChecked(true);
                prefs.edit().putString("sort", "name").apply();
                return true;

            case R.id.menu_sort_uid:
                item.setChecked(true);
                prefs.edit().putString("sort", "uid").apply();
                return true;

            case R.id.menu_lockdown:
                menu_lockdown(item);
                return true;

            case R.id.menu_log:
                startActivity(new Intent(this, ActivityLog.class));
                return true;

            case R.id.menu_settings:
                startActivity(new Intent(this, ActivitySettings.class));
                return true;

            case R.id.menu_invite:
                startActivityForResult(getIntentInvite(this), REQUEST_INVITE);
                return true;

            case R.id.menu_legend:
                menu_legend();
                return true;

            case R.id.menu_support:
                startActivity(getIntentSupport());
                return true;

            case R.id.menu_about:
                menu_about();
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void showHints() {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean hintUsage = prefs.getBoolean("hint_usage", true);

        // Hint white listing
        final LinearLayout llWhitelist = findViewById(R.id.llWhitelist);
        Button btnWhitelist = findViewById(R.id.btnWhitelist);
        boolean whitelist_wifi = prefs.getBoolean("whitelist_wifi", false);
        boolean whitelist_other = prefs.getBoolean("whitelist_other", false);
        boolean hintWhitelist = prefs.getBoolean("hint_whitelist", true);
        llWhitelist.setVisibility(!(whitelist_wifi || whitelist_other) && hintWhitelist && !hintUsage ? View.VISIBLE : View.GONE);
        btnWhitelist.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                prefs.edit().putBoolean("hint_whitelist", false).apply();
                llWhitelist.setVisibility(View.GONE);
            }
        });

        // Hint push messages
        final LinearLayout llPush = findViewById(R.id.llPush);
        Button btnPush = findViewById(R.id.btnPush);
        boolean hintPush = prefs.getBoolean("hint_push", true);
        llPush.setVisibility(hintPush && !hintUsage ? View.VISIBLE : View.GONE);
        btnPush.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                prefs.edit().putBoolean("hint_push", false).apply();
                llPush.setVisibility(View.GONE);
            }
        });

        // Hint system applications
        final LinearLayout llSystem = findViewById(R.id.llSystem);
        Button btnSystem = findViewById(R.id.btnSystem);
        boolean system = prefs.getBoolean("manage_system", false);
        boolean hintSystem = prefs.getBoolean("hint_system", true);
        llSystem.setVisibility(!system && hintSystem ? View.VISIBLE : View.GONE);
        btnSystem.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                prefs.edit().putBoolean("hint_system", false).apply();
                llSystem.setVisibility(View.GONE);
            }
        });
    }

    private void initAds() {
        MobileAds.initialize(this,
                "ca-app-pub-5245039317944205~8750067306");
        mAdView = findViewById(R.id.adView);
        AdRequest adRequest = new AdRequest.Builder()
                .addTestDevice("33BE2250B43518CCDA7DE426D04EE231")
                .build();
        mAdView.loadAd(adRequest);
    }





    private void checkExtras(Intent intent) {
        // Approve request
        if (intent.hasExtra(EXTRA_APPROVE)) {
            Log.i(TAG, "Requesting VPN approval");
            swEnabled.toggle();
        }

        if (intent.hasExtra(EXTRA_LOGCAT)) {
            Log.i(TAG, "Requesting logcat");
            Intent logcat = getIntentLogcat();
            if (logcat.resolveActivity(getPackageManager()) != null)
                startActivityForResult(logcat, REQUEST_LOGCAT);
        }
    }

    private void updateApplicationList(final String search) {
        Log.i(TAG, "Update search=" + search);

        new AsyncTask<Object, Object, List<Rule>>() {
            private boolean refreshing = true;

            @Override
            protected void onPreExecute() {
                swipeRefresh.post(new Runnable() {
                    @Override
                    public void run() {
                        if (refreshing)
                            swipeRefresh.setRefreshing(true);
                    }
                });
            }

            @Override
            protected List<Rule> doInBackground(Object... arg) {
                return Rule.getRules(false, ActivityMain.this);
            }

            @Override
            protected void onPostExecute(List<Rule> result) {
                if (running) {
                    if (adapter != null) {
                        adapter.set(result);
                        updateSearch(search);
                    }

                    if (swipeRefresh != null) {
                        refreshing = false;
                        swipeRefresh.setRefreshing(false);
                    }
                }
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    private void updateSearch(String search) {
        if (menuSearch != null) {
            SearchView searchView = (SearchView) menuSearch.getActionView();
            if (search == null) {
                if (menuSearch.isActionViewExpanded())
                    adapter.getFilter().filter(searchView.getQuery().toString());
            } else {
                menuSearch.expandActionView();
                searchView.setQuery(search, true);
            }
        }
    }

    private void checkDoze() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            final Intent doze = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
            if (Util.batteryOptimizing(this) && getPackageManager().resolveActivity(doze, 0) != null) {
                final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
                if (!prefs.getBoolean("nodoze", false)) {
                    LayoutInflater inflater = LayoutInflater.from(this);
                    View view = inflater.inflate(R.layout.doze, null, false);
                    final CheckBox cbDontAsk = view.findViewById(R.id.cbDontAsk);
                    dialogDoze = new AlertDialog.Builder(this)
                            .setView(view)
                            .setCancelable(true)
                            .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    prefs.edit().putBoolean("nodoze", cbDontAsk.isChecked()).apply();
                                    startActivity(doze);
                                }
                            })
                            .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    prefs.edit().putBoolean("nodoze", cbDontAsk.isChecked()).apply();
                                }
                            })
                            .setOnDismissListener(new DialogInterface.OnDismissListener() {
                                @Override
                                public void onDismiss(DialogInterface dialogInterface) {
                                    dialogDoze = null;
                                    checkDataSaving();
                                }
                            })
                            .create();
                   // dialogDoze.show();
                } else
                    checkDataSaving();
            } else
                checkDataSaving();
        }
    }

    private void checkDataSaving() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            final Intent settings = new Intent(
                    Settings.ACTION_IGNORE_BACKGROUND_DATA_RESTRICTIONS_SETTINGS,
                    Uri.parse("package:" + getPackageName()));
            if (Util.dataSaving(this) && getPackageManager().resolveActivity(settings, 0) != null) {
                final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
                if (!prefs.getBoolean("nodata", false)) {
                    LayoutInflater inflater = LayoutInflater.from(this);
                    View view = inflater.inflate(R.layout.datasaving, null, false);
                    final CheckBox cbDontAsk = view.findViewById(R.id.cbDontAsk);
                    dialogDoze = new AlertDialog.Builder(this)
                            .setView(view)
                            .setCancelable(true)
                            .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    prefs.edit().putBoolean("nodata", cbDontAsk.isChecked()).apply();
                                    startActivity(settings);
                                }
                            })
                            .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    prefs.edit().putBoolean("nodata", cbDontAsk.isChecked()).apply();
                                }
                            })
                            .setOnDismissListener(new DialogInterface.OnDismissListener() {
                                @Override
                                public void onDismiss(DialogInterface dialogInterface) {
                                    dialogDoze = null;
                                }
                            })
                            .create();
                    dialogDoze.show();
                }
            }
        }
    }

    private void menu_legend() {
        TypedValue tv = new TypedValue();
        getTheme().resolveAttribute(R.attr.colorOn, tv, true);
        int colorOn = tv.data;
        getTheme().resolveAttribute(R.attr.colorOff, tv, true);
        int colorOff = tv.data;

        // Create view
        LayoutInflater inflater = LayoutInflater.from(this);
        View view = inflater.inflate(R.layout.legend, null, false);
        ImageView ivLockdownOn = view.findViewById(R.id.ivLockdownOn);
        ImageView ivWifiOn = view.findViewById(R.id.ivWifiOn);
        ImageView ivWifiOff = view.findViewById(R.id.ivWifiOff);
        ImageView ivOtherOn = view.findViewById(R.id.ivOtherOn);
        ImageView ivOtherOff = view.findViewById(R.id.ivOtherOff);
        ImageView ivScreenOn = view.findViewById(R.id.ivScreenOn);
        ImageView ivHostAllowed = view.findViewById(R.id.ivHostAllowed);
        ImageView ivHostBlocked = view.findViewById(R.id.ivHostBlocked);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            Drawable wrapLockdownOn = DrawableCompat.wrap(ivLockdownOn.getDrawable());
            Drawable wrapWifiOn = DrawableCompat.wrap(ivWifiOn.getDrawable());
            Drawable wrapWifiOff = DrawableCompat.wrap(ivWifiOff.getDrawable());
            Drawable wrapOtherOn = DrawableCompat.wrap(ivOtherOn.getDrawable());
            Drawable wrapOtherOff = DrawableCompat.wrap(ivOtherOff.getDrawable());
            Drawable wrapScreenOn = DrawableCompat.wrap(ivScreenOn.getDrawable());
            Drawable wrapHostAllowed = DrawableCompat.wrap(ivHostAllowed.getDrawable());
            Drawable wrapHostBlocked = DrawableCompat.wrap(ivHostBlocked.getDrawable());

            DrawableCompat.setTint(wrapLockdownOn, colorOff);
            DrawableCompat.setTint(wrapWifiOn, colorOn);
            DrawableCompat.setTint(wrapWifiOff, colorOff);
            DrawableCompat.setTint(wrapOtherOn, colorOn);
            DrawableCompat.setTint(wrapOtherOff, colorOff);
            DrawableCompat.setTint(wrapScreenOn, colorOn);
            DrawableCompat.setTint(wrapHostAllowed, colorOn);
            DrawableCompat.setTint(wrapHostBlocked, colorOff);
        }


        // Show dialog
        dialogLegend = new AlertDialog.Builder(this)
                .setView(view)
                .setCancelable(true)
                .setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialogInterface) {
                        dialogLegend = null;
                    }
                })
                .create();
        dialogLegend.show();
    }

    private void menu_lockdown(MenuItem item) {
        item.setChecked(!item.isChecked());
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.edit().putBoolean("lockdown", item.isChecked()).apply();
        ServiceSinkhole.reload("lockdown", this, false);
        WidgetLockdown.updateWidgets(this);
    }

    private void menu_about() {
        // Create view
        LayoutInflater inflater = LayoutInflater.from(this);
        View view = inflater.inflate(R.layout.about, null, false);
        TextView tvVersionName = view.findViewById(R.id.tvVersionName);
        TextView tvVersionCode = view.findViewById(R.id.tvVersionCode);
        Button btnRate = view.findViewById(R.id.btnRate);
        TextView tvLicense = view.findViewById(R.id.tvLicense);
        TextView tvPrivacy = view.findViewById(R.id.tvPrivacy);
        TextView tvAdmob = view.findViewById(R.id.tvAdmob);

        // Show version
        tvVersionName.setText(Util.getSelfVersionName(this));
        if (!Util.hasValidFingerprint(this))
            tvVersionName.setTextColor(Color.GRAY);
        tvVersionCode.setText(Integer.toString(Util.getSelfVersionCode(this)));

        // Handle license
        tvLicense.setMovementMethod(LinkMovementMethod.getInstance());
        tvLicense.setMovementMethod(LinkMovementMethod.getInstance());
        tvPrivacy.setMovementMethod(LinkMovementMethod.getInstance());
        tvAdmob.setVisibility(IAB.isPurchasedAny(this) ? View.GONE : View.VISIBLE);

        // Handle logcat
        view.setOnClickListener(new View.OnClickListener() {
            private short tap = 0;
            private Toast toast = Toast.makeText(ActivityMain.this, "", Toast.LENGTH_SHORT);

            @Override
            public void onClick(View view) {
                tap++;
                if (tap == 7) {
                    tap = 0;
                    toast.cancel();

                    Intent intent = getIntentLogcat();
                    if (intent.resolveActivity(getPackageManager()) != null)
                        startActivityForResult(intent, REQUEST_LOGCAT);

                } else if (tap > 3) {
                    toast.setText(Integer.toString(7 - tap));
                    toast.show();
                }
            }
        });

        // Handle rate
        btnRate.setVisibility(getIntentRate(this).resolveActivity(getPackageManager()) == null ? View.GONE : View.VISIBLE);
        btnRate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(getIntentRate(ActivityMain.this));
            }
        });

        // Show dialog
        dialogAbout = new AlertDialog.Builder(this)
                .setView(view)
                .setCancelable(true)
                .setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialogInterface) {
                        dialogAbout = null;
                    }
                })
                .create();
        dialogAbout.show();
    }

    private static Intent getIntentInvite(Context context) {
        Intent intent = new Intent("com.google.android.gms.appinvite.ACTION_APP_INVITE");
        intent.setPackage("com.google.android.gms");
        intent.putExtra("com.google.android.gms.appinvite.TITLE", context.getString(R.string.menu_invite));
        intent.putExtra("com.google.android.gms.appinvite.MESSAGE", context.getString(R.string.msg_try));
        intent.putExtra("com.google.android.gms.appinvite.BUTTON_TEXT", context.getString(R.string.msg_try));
        // com.google.android.gms.appinvite.DEEP_LINK_URL
        return intent;
    }

    private static Intent getIntentRate(Context context) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + context.getPackageName()));
        if (intent.resolveActivity(context.getPackageManager()) == null)
            intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + context.getPackageName()));
        return intent;
    }

    private static Intent getIntentSupport() {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse("http://internetguard.sheikhsoft.com/faq.php"));
        return intent;
    }

    private Intent getIntentLogcat() {
        Intent intent;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            if (Util.isPackageInstalled("org.openintents.filemanager", this)) {
                intent = new Intent("org.openintents.action.PICK_DIRECTORY");
            } else {
                intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(Uri.parse("https://play.google.com/store/apps/details?id=org.openintents.filemanager"));
            }
        } else {
            intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_TITLE, "logcat.txt");
        }
        return intent;
    }

    /**
     * Fab icon Custom Code
     */



    public void animate(View view) {
        if (!mIsCancel) {
            mBinding.adView.setVisibility(View.GONE);
            if (mStartX == 0.0f) {
                mStartX = view.getX();
                mStartY = view.getY();

                mBottomX = getBottomFilterXPosition();
                mBottomY = getBottomFilterYPosition();

                mBottomListStartY = mBinding.bottomListBackground.getY();
            }

            final int x = getFinalXPosition();
            final int y = getFinalYPosition();


            ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);

            animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    float v = (float) animation.getAnimatedValue();

                    mBinding.fab.setX(
                            x + (mStartX - x - ((mStartX - x) * v))
                    );

                    mBinding.fab.setY(
                            y + (mStartY - y - ((mStartY - y) * (v * v)))
                    );
                }
            });
            animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    super.onAnimationEnd(animation);

                    removeFabBackground();

                    new Handler().postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            mBinding.fab.animate()
                                    .y(mBottomY)
                                    .setDuration(200)
                                    .start();

                        }
                    },50);
                    new Handler().postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            mBinding.cancel.setVisibility(VISIBLE);
                            mBinding.cancel.setTranslationX(-(mBottomX - x));

                            mBinding.cancel.animate()
                                    .translationXBy(mBottomX - x)
                                    .setDuration(200)
                                    .start();

                            mBinding.fab.animate()
                                    .x(mBottomX)
                                    .setDuration(200)
                                    .start();

                            mBinding.fab.animate()
                                    .x(mBottomX)
                                    .setDuration(200)
                                    .start();

                            mBinding.sheetTop.setScaleY(0f);
                            mBinding.sheetTop.setVisibility(VISIBLE);

                            mBinding.sheetTop.animate()
                                    .scaleY(1f)
                                    .setListener(new AnimatorListenerAdapter() {
                                        @Override
                                        public void onAnimationEnd(Animator animation) {
                                            super.onAnimationEnd(animation);
                                            mBinding.scroll.setVisibility(VISIBLE);
                                        }
                                    })
                                    .setDuration(200)
                                    .start();
                        }
                    }, 200);

                    if (resetBottomList) {
                        Log.d(LOG_TAG, "onAnimationEnd: reset");
                        resetBottomListBackground();
                    }


                    new Handler().postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            mBinding.bottomListBackground.animate()
                                    .alpha(1f)
                                    .setDuration(500)
                                    .setListener(new AnimatorListenerAdapter() {
                                        @Override
                                        public void onAnimationEnd(Animator animation) {
                                            super.onAnimationEnd(animation);
                                            mBinding.fab.setImageResource(R.drawable.cancel);
                                            mBinding.fab.setVisibility(INVISIBLE);
                                            mBinding.fab.setX(mBinding.cancel.getX() - mDisplayMetrics.density * 4);
                                            mBinding.fab.setY(getBottomFilterYPosition());
                                            mBinding.applyFilters.setVisibility(VISIBLE);
                                        }
                                    })
                                    .start();
                        }
                    }, 400);

                    revealFilterSheet(y);
                }
            });

            animator.start();
        } else {
            mBinding.fab.setImageResource(R.drawable.filter);
            mBinding.fab.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.sheet)));
            mIsCancel = false;
        }
    }

    private void resetBottomListBackground() {
        resetBottomList = false;
        mBinding.bottomListBackground.setVisibility(VISIBLE);
        Drawable d = mBinding.bottomListBackground.getBackground();
        final GradientDrawable gd = (GradientDrawable) d;
        mBinding.bottomListBackground.setAlpha(0f);
        gd.setCornerRadius(0f);


        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) mBinding.bottomListBackground.getLayoutParams();
        params.width = -1;
        params.height = (int) (mDisplayMetrics.density * 64);
        mBinding.bottomListBackground.setY(mBottomListStartY + mDisplayMetrics.density * 8);
        mBinding.bottomListBackground.requestLayout();
    }

    private int getBottomFilterYPosition() {
        return (int) (
                mBinding.applyFilters.getY()
                        + (mDisplayMetrics.heightPixels - getStatusBarHeight() - mDisplayMetrics.density * 64)
                        - mDisplayMetrics.density * 4);
    }

    private int getBottomFilterXPosition() {
        return (int) (
                mBinding.applyFilters.getX()
                        + mDisplayMetrics.widthPixels / 2
                        - mDisplayMetrics.density * 4);
    }

    private void removeFabBackground() {
        mBinding.fab.setBackgroundTintList(ColorStateList.valueOf(TRANSPARENT));

        mBinding.fab.setElevation(0f);
    }

    private void revealFilterSheet(int y) {
        mBinding.reveal.setVisibility(VISIBLE);

        Animator a = ViewAnimationUtils.createCircularReveal(
                mBinding.reveal,
                mDisplayMetrics.widthPixels / 2,
                (int) (y - mBinding.reveal.getY()) + getFabSize() / 2,
                getFabSize() / 2,
                mBinding.reveal.getHeight() * .7f);
        a.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                mBinding.list.setVisibility(VISIBLE);
            }
        });
        a.start();
    }

    public int getFinalXPosition() {
        return mDisplayMetrics.widthPixels / 2 - getFabSize() / 2;
    }

    public int getFinalYPosition() {
        int marginFromBottom = getFinalYPositionFromBottom();
        return mDisplayMetrics.heightPixels - marginFromBottom + getFabSize() / 2;
    }

    public void setDisplayMetrics() {
        mDisplayMetrics = getResources().getDisplayMetrics();
    }

    public int getFinalYPositionFromBottom() {
        return (int) (mDisplayMetrics.density * 250);
    }

    public int getFabSize() {
        return (int) (mDisplayMetrics.density * 56);
    }

    public void acceptFilters(View view) {
        mBinding.fab.setVisibility(VISIBLE);
        mBinding.list.setVisibility(INVISIBLE);
        mBinding.scroll.setVisibility(INVISIBLE);

        mIsCancel = true;
        final int x = getFinalXPosition();
        final int y = getFinalYPosition();


        mBinding.applyFilters.setVisibility(INVISIBLE);
        mBinding.cancel.setVisibility(INVISIBLE);

        final int startX = (int) mBinding.fab.getX();
        final int startY = (int) mBinding.fab.getY();

        mBinding.sheetTop.setVisibility(INVISIBLE);
        Animator reveal = ViewAnimationUtils.createCircularReveal(
                mBinding.reveal,
                mDisplayMetrics.widthPixels / 2,
                (int) (y - mBinding.reveal.getY()) + getFabSize() / 2,
                mBinding.reveal.getHeight() * .5f,
                getFabSize() / 2);

        reveal.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                mBinding.reveal.setVisibility(INVISIBLE);
                mBinding.fab.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(ActivityMain
                        .this, R.color.colorAccent)));
                mBinding.fab.setElevation(mDisplayMetrics.density * 4);

            }
        });
        reveal.start();

        animateBottomSheet();

        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float v = (float) animation.getAnimatedValue();

                mBinding.fab.setX(
                        x - (x - startX - ((x - startX) * v))
                );

                mBinding.fab.setY(
                        y + (startY - y - ((startY - y) * (v * v)))
                );


            }
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                mBinding.fab.animate()
                        .rotationBy(360)
                        .setDuration(1000)
                        .start();
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        returnFabToInitialPosition();
                        mBinding.bottomListBackground.setVisibility(INVISIBLE);
                    }
                }, 1000);
            }
        });
        animator.start();
    }

    private void animateBottomSheet() {
        Drawable d = mBinding.bottomListBackground.getBackground();
        final GradientDrawable gd = (GradientDrawable) d;


        final RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams)
                mBinding.bottomListBackground.getLayoutParams();

        final int startWidth = mBinding.bottomListBackground.getWidth();
        final int startHeight = mBinding.bottomListBackground.getHeight();
        final int startY = (int) mBinding.bottomListBackground.getY();


        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float v = (float) animation.getAnimatedValue();
                gd.setCornerRadius(mDisplayMetrics.density * 50 * v);

                int i = (int) (startWidth - (startWidth - getFabSize()) * v);
                params.width = i;
                params.height = (int) (startHeight - (startHeight - getFabSize()) * v);
                mBinding.bottomListBackground.setY(getFinalYPosition() + (startY
                        - getFinalYPosition()) - ((startY - getFinalYPosition()) * v));

                mBinding.bottomListBackground.requestLayout();
            }
        });
        animator.start();
    }

    private void returnFabToInitialPosition() {
        final int x = getFinalXPosition();
        final int y = getFinalYPosition();
        resetBottomList = true;




        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float v = (float) animation.getAnimatedValue();

                mBinding.fab.setX(
                        x + ((mStartX - x) * v)
                );

                mBinding.fab.setY(
                        (float) (y + (mStartY - y) * (Math.pow(v, .5f)))
                );
            }
        });
        animator.start();

        mBinding.fab.setImageResource(R.drawable.settings);
        mBinding.fab.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.sheet)));
        mIsCancel = false;

        mBinding.adView.setVisibility(VISIBLE);
    }

    public int getStatusBarHeight() {
        return (int) (mDisplayMetrics.density * 24);
    }




}