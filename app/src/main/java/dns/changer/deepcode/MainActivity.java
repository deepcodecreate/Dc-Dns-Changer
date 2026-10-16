package dns.changer.deepcode;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

/**
 * Single-activity host: a floating Telegram-style tab bar switches between the
 * four main screens, each implemented as a Fragment (previously separate Activities).
 */
public class MainActivity extends BaseActivity {

    private static final String TAG_HOME = "home";
    private static final String TAG_SERVERS = "servers";
    private static final String TAG_LOG = "log";
    private static final String TAG_SETTINGS = "settings";
    private static final String STATE_SELECTED_TAB = "selected_tab";

    private FloatingNavBar bottomNav;
    private Fragment homeFragment, serversFragment, logFragment, settingsFragment;
    private Fragment activeFragment;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeManager.applyTheme(this);
        setContentView(R.layout.activity_main);

        bottomNav = findViewById(R.id.bottom_nav);
        setupBottomNavigation();
        FragmentManager fm = getSupportFragmentManager();

        if (savedInstanceState == null) {
            homeFragment = new HomeFragment();
            serversFragment = new ServersFragment();
            logFragment = new LogFragment();
            settingsFragment = new SettingsFragment();

            FragmentTransaction tx = fm.beginTransaction();
            tx.add(R.id.fragment_container, settingsFragment, TAG_SETTINGS).hide(settingsFragment);
            tx.add(R.id.fragment_container, logFragment, TAG_LOG).hide(logFragment);
            tx.add(R.id.fragment_container, serversFragment, TAG_SERVERS).hide(serversFragment);
            tx.add(R.id.fragment_container, homeFragment, TAG_HOME);
            tx.commitNow();
            activeFragment = homeFragment;
        } else {
            homeFragment = fm.findFragmentByTag(TAG_HOME);
            serversFragment = fm.findFragmentByTag(TAG_SERVERS);
            logFragment = fm.findFragmentByTag(TAG_LOG);
            settingsFragment = fm.findFragmentByTag(TAG_SETTINGS);
            int savedId = savedInstanceState.getInt(STATE_SELECTED_TAB, R.id.nav_home);
            activeFragment = fragmentFor(savedId);
            if (activeFragment == null) activeFragment = homeFragment;
        }

        bottomNav.setOnItemSelectedListener(itemId -> {
            Fragment target = fragmentFor(itemId);
            if (target == null) return false;
            switchTo(target);
            return true;
        });

        if (savedInstanceState != null) {
            bottomNav.setSelectedItemId(idFor(activeFragment));
        }

        handleIntent(getIntent());
    }

    /**
     * Fills the floating tab bar with the four destinations and paints it with
     * the active theme. Colors come from ThemeManager (plain ints) rather than
     * theme-attribute selectors, which crash on some MIUI builds.
     */
    private void setupBottomNavigation() {
        if (bottomNav == null) return;
        ThemeManager.ThemeDef def = ThemeManager.byId(ThemeManager.getSelectedId(this));
        bottomNav.setColors(def.previewBg, def.previewAccent, def.light);
        bottomNav.addItem(R.id.nav_home, R.drawable.ic_nav_home, getString(R.string.nav_home));
        bottomNav.addItem(R.id.nav_servers, R.drawable.ic_dns_active, getString(R.string.nav_servers));
        bottomNav.addItem(R.id.nav_log, R.drawable.ic_nav_log, getString(R.string.nav_log));
        bottomNav.addItem(R.id.nav_settings, R.drawable.ic_setting, getString(R.string.nav_settings));
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (intent == null || intent.getData() == null) return;
        Uri data = intent.getData();
        if (!"dnschanger".equals(data.getScheme()) || !"add".equals(data.getHost())) return;
        bottomNav.setSelectedItemId(R.id.nav_servers);
        if (serversFragment instanceof ServersFragment) {
            ((ServersFragment) serversFragment).handleDeepLink(data);
        }
    }

    private Fragment fragmentFor(int itemId) {
        if (itemId == R.id.nav_home) return homeFragment;
        if (itemId == R.id.nav_servers) return serversFragment;
        if (itemId == R.id.nav_log) return logFragment;
        if (itemId == R.id.nav_settings) return settingsFragment;
        return null;
    }

    private int idFor(Fragment fragment) {
        if (fragment == serversFragment) return R.id.nav_servers;
        if (fragment == logFragment) return R.id.nav_log;
        if (fragment == settingsFragment) return R.id.nav_settings;
        return R.id.nav_home;
    }

    private void switchTo(Fragment target) {
        if (target == null || target == activeFragment) return;
        if (target instanceof ServersFragment) {
            ((ServersFragment) target).syncProtocolFromPreferences();
        }
        FragmentTransaction tx = getSupportFragmentManager().beginTransaction();
        tx.setCustomAnimations(R.anim.fragment_fade_in, R.anim.fragment_fade_out,
                R.anim.fragment_fade_in, R.anim.fragment_fade_out);
        if (activeFragment != null) tx.hide(activeFragment);
        tx.show(target);
        tx.commit();
        activeFragment = target;
    }

    /** Lets a hosted fragment switch the visible tab (e.g. after picking a server). */
    public void selectTab(int menuItemId) {
        bottomNav.setSelectedItemId(menuItemId);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_SELECTED_TAB, idFor(activeFragment));
    }
}
