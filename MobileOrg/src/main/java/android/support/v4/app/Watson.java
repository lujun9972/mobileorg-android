package android.support.v4.app;

import android.util.Log;
import android.view.View;
import android.view.Window;
import com.actionbarsherlock.ActionBarSherlock;
import com.actionbarsherlock.ActionBarSherlock.OnCreatePanelMenuListener;
import com.actionbarsherlock.ActionBarSherlock.OnMenuItemSelectedListener;
import com.actionbarsherlock.ActionBarSherlock.OnPreparePanelListener;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;

/**
 * Patched version of ActionBarSherlock's Watson that is compatible with
 * support-v4 25.4.0. The original Watson accessed FragmentActivity.mFragments
 * (which was FragmentManagerImpl) directly to iterate over added fragments for
 * menu handling. In support-v4 r21+, mFragments became FragmentController,
 * breaking the field access. Since no fragments in MobileOrg implement the
 * Watson menu listener interfaces, the fragment menu iteration is removed.
 */
public abstract class Watson extends FragmentActivity implements OnCreatePanelMenuListener, OnPreparePanelListener, OnMenuItemSelectedListener {
    private static final String TAG = "Watson";

    /** Fragment interface for menu creation callback. */
    public interface OnCreateOptionsMenuListener {
        public void onCreateOptionsMenu(Menu menu, MenuInflater inflater);
    }
    /** Fragment interface for menu preparation callback. */
    public interface OnPrepareOptionsMenuListener {
        public void onPrepareOptionsMenu(Menu menu);
    }
    /** Fragment interface for menu item selection callback. */
    public interface OnOptionsItemSelectedListener {
        public boolean onOptionsItemSelected(MenuItem item);
    }


    ///////////////////////////////////////////////////////////////////////////
    // Sherlock menu handling
    ///////////////////////////////////////////////////////////////////////////

    @Override
    public boolean onCreatePanelMenu(int featureId, Menu menu) {
        if (ActionBarSherlock.DEBUG) Log.d(TAG, "[onCreatePanelMenu] featureId: " + featureId + ", menu: " + menu);

        if (featureId == Window.FEATURE_OPTIONS_PANEL) {
            boolean result = onCreateOptionsMenu(menu);
            if (ActionBarSherlock.DEBUG) Log.d(TAG, "[onCreatePanelMenu] returning " + result);
            return result;
        }
        return false;
    }

    @Override
    public boolean onPreparePanel(int featureId, View view, Menu menu) {
        if (ActionBarSherlock.DEBUG) Log.d(TAG, "[onPreparePanel] featureId: " + featureId + ", view: " + view + " menu: " + menu);

        if (featureId == Window.FEATURE_OPTIONS_PANEL) {
            boolean result = onPrepareOptionsMenu(menu);
            if (ActionBarSherlock.DEBUG) Log.d(TAG, "[onPreparePanel] returning " + result);
            result &= menu.hasVisibleItems();
            return result;
        }
        return false;
    }

    @Override
    public boolean onMenuItemSelected(int featureId, MenuItem item) {
        if (ActionBarSherlock.DEBUG) Log.d(TAG, "[onMenuItemSelected] featureId: " + featureId + ", item: " + item);

        if (featureId == Window.FEATURE_OPTIONS_PANEL) {
            return onOptionsItemSelected(item);
        }
        return false;
    }

    public abstract boolean onCreateOptionsMenu(Menu menu);

    public abstract boolean onPrepareOptionsMenu(Menu menu);

    public abstract boolean onOptionsItemSelected(MenuItem item);

    public abstract MenuInflater getSupportMenuInflater();
}
