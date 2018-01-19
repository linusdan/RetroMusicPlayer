package com.name.monkey.retromusic.ui.activities.base;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.os.Bundle;
import android.support.annotation.ColorInt;
import android.support.annotation.FloatRange;
import android.support.annotation.LayoutRes;
import android.support.design.widget.BottomNavigationView;
import android.support.transition.TransitionManager;
import android.support.v4.app.Fragment;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.PathInterpolator;

import com.name.monkey.retromusic.ui.fragments.player.NowPlayingScreen;
import com.sothree.slidinguppanel.SlidingUpPanelLayout;

import butterknife.BindView;
import butterknife.ButterKnife;
import code.name.monkey.appthemehelper.ThemeStore;
import code.name.monkey.appthemehelper.util.ATHUtil;
import code.name.monkey.appthemehelper.util.ColorUtil;
import code.name.monkey.retromusic.R;
import code.name.monkey.retromusic.helper.MusicPlayerRemote;
import code.name.monkey.retromusic.ui.activities.base.AbsMusicServiceActivity;
import code.name.monkey.retromusic.ui.fragments.MiniPlayerFragment;
import code.name.monkey.retromusic.ui.fragments.base.AbsPlayerFragment;
import code.name.monkey.retromusic.ui.fragments.player.blur.BlurPlayerFragment;
import code.name.monkey.retromusic.ui.fragments.player.flat.FlatPlayerFragment;
import code.name.monkey.retromusic.ui.fragments.player.full.FullPlayerFragment;
import code.name.monkey.retromusic.ui.fragments.player.hmm.HmmPlayerFragment;
import code.name.monkey.retromusic.ui.fragments.player.normal.PlayerFragment;
import code.name.monkey.retromusic.ui.fragments.player.plain.PlainPlayerFragment;
import code.name.monkey.retromusic.ui.fragments.player.simple.SimplePlayerFragment;
import code.name.monkey.retromusic.util.PreferenceUtil;
import code.name.monkey.retromusic.util.ViewUtil;
import code.name.monkey.retromusic.views.BottomNavigationViewEx;

public abstract class AbsSlidingMusicPanelActivity extends AbsMusicServiceActivity implements
        BottomNavigationView.OnNavigationItemSelectedListener,
        SlidingUpPanelLayout.PanelSlideListener,
        PlayerFragment.Callbacks {

    public static final String TAG = AbsSlidingMusicPanelActivity.class.getSimpleName();

    @BindView(R.id.bottom_navigation)
    BottomNavigationViewEx mBottomNavigationView;
    @BindView(R.id.sliding_layout)
    SlidingUpPanelLayout mSlidingUpPanelLayout;
    @BindView(R.id.root_layout)
    ViewGroup mViewGroup;

    private int mTaskColor;
    private boolean mLightStatusbar;
    private NowPlayingScreen currentNowPlayingScreen;
    private AbsPlayerFragment mPlayerFragment;
    private MiniPlayerFragment mMiniPlayerFragment;
    private int mNavigationbarColor;
    private ValueAnimator navigationBarColorAnimator;
    private ArgbEvaluator mArgbEvaluator = new ArgbEvaluator();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(createContentView());
        ButterKnife.bind(this);

        currentNowPlayingScreen = PreferenceUtil.getInstance(this).getNowPlayingScreen();
        Fragment fragment; // must implement AbsPlayerFragment
        switch (currentNowPlayingScreen) {
            case BLUR:
                fragment = new BlurPlayerFragment();
                break;
            case FLAT:
                fragment = new FlatPlayerFragment();
                break;
            case PLAIN:
                fragment = new PlainPlayerFragment();
                break;
            case SIMPLE:
                fragment = new SimplePlayerFragment();
                break;
            case TINY:
                fragment = new HmmPlayerFragment();
                break;
            case FULL:
                fragment = new FullPlayerFragment();
                break;
            case NORMAL:
            default:
                fragment = new PlayerFragment();
                break;
        }
        getSupportFragmentManager().beginTransaction().replace(R.id.player_fragment_container, fragment).commit();
        getSupportFragmentManager().executePendingTransactions();

        mPlayerFragment = (AbsPlayerFragment) getSupportFragmentManager().findFragmentById(R.id.player_fragment_container);
        mMiniPlayerFragment = (MiniPlayerFragment) getSupportFragmentManager().findFragmentById(R.id.mini_player_fragment);

        //noinspection ConstantConditions
        mMiniPlayerFragment.getView().setOnClickListener(v -> expandPanel());
        mSlidingUpPanelLayout.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                mSlidingUpPanelLayout.getViewTreeObserver().removeOnGlobalLayoutListener(this);

                if (getPanelState() == SlidingUpPanelLayout.PanelState.EXPANDED) {
                    onPanelSlide(mSlidingUpPanelLayout, 1);
                    onPanelExpanded(mSlidingUpPanelLayout);
                } else if (getPanelState() == SlidingUpPanelLayout.PanelState.COLLAPSED) {
                    onPanelCollapsed(mSlidingUpPanelLayout);
                } else {
                    mPlayerFragment.onHide();
                }
            }
        });

        if (PreferenceUtil.getInstance(this).isGenreShown())
            mBottomNavigationView.getMenu().removeItem(R.id.action_genre);

        setupBottomView();
        mSlidingUpPanelLayout.addPanelSlideListener(this);

    }

    private void setupBottomView() {
        mBottomNavigationView.setOnNavigationItemSelectedListener(this);
        mBottomNavigationView.enableAnimation(false);
        mBottomNavigationView.enableItemShiftingMode(false);
        mBottomNavigationView.enableShiftingMode(false);
        mBottomNavigationView.setTextVisibility(PreferenceUtil.getInstance(this).tabTitles());
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (currentNowPlayingScreen != PreferenceUtil.getInstance(this).getNowPlayingScreen()) {
            postRecreate();
        }
    }

    public void setAntiDragView(View antiDragView) {
        mSlidingUpPanelLayout.setAntiDragView(antiDragView);
    }

    protected abstract View createContentView();

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        if (!MusicPlayerRemote.getPlayingQueue().isEmpty()) {
            mSlidingUpPanelLayout.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    mSlidingUpPanelLayout.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    hideBottomBar(false);
                }
            });
        }// don't call hideBottomBar(true) here as it causes a bug with the SlidingUpPanelLayout
    }

    @Override
    public void onQueueChanged() {
        super.onQueueChanged();
        hideBottomBar(MusicPlayerRemote.getPlayingQueue().isEmpty());
    }

    @Override
    public void onPanelSlide(View panel, @FloatRange(from = 0, to = 1) float slideOffset) {
        mBottomNavigationView.setTranslationY(slideOffset * 300);
        setMiniPlayerAlphaProgress(slideOffset);

        //if (navigationBarColorAnimator != null) navigationBarColorAnimator.cancel();
        //super.setNavigationbarColor((int) mArgbEvaluator.evaluate(slideOffset, mNavigationbarColor, mPlayerFragment.getPaletteColor()));
    }

    @Override
    public void onPanelStateChanged(View panel, SlidingUpPanelLayout.PanelState previousState, SlidingUpPanelLayout.PanelState newState) {
        switch (newState) {
            case COLLAPSED:
                onPanelCollapsed(panel);
                break;
            case EXPANDED:
                onPanelExpanded(panel);
                break;
            case ANCHORED:
                collapsePanel(); // this fixes a bug where the panel would get stuck for some reason
                break;
        }
    }

    public void onPanelCollapsed(View panel) {
        // restore values
        super.setLightStatusbar(mLightStatusbar);
        super.setTaskDescriptionColor(mTaskColor);
        super.setNavigationbarColor(ThemeStore.primaryColor(this));

        mPlayerFragment.setMenuVisibility(false);
        mPlayerFragment.setUserVisibleHint(false);
        mPlayerFragment.onHide();

    }

    public void onPanelExpanded(View panel) {
        // setting fragments values
        int playerFragmentColor = mPlayerFragment.getPaletteColor();

        super.setTaskDescriptionColor(playerFragmentColor);
        super.setNavigationbarColor(ThemeStore.primaryColor(this));

        if (PreferenceUtil.getInstance(this).getAdaptiveColor() || ATHUtil.isWindowBackgroundDark(this) || (currentNowPlayingScreen == NowPlayingScreen.TINY)) {
            super.setLightStatusbar(true);
        } else
            super.setLightStatusbar(false);

        mPlayerFragment.setMenuVisibility(true);
        mPlayerFragment.setUserVisibleHint(true);
        mPlayerFragment.onShow();
    }

    @Override
    public void onPaletteColorChanged() {
        int playerFragmentColor = mPlayerFragment.getPaletteColor();
        //if (ATHUtil.isWindowBackgroundDark(this) && !PreferenceUtil.getInstance(this).isDominantColor())
        //animateNavigationBarColor(playerFragmentColor);

        if (getPanelState() == SlidingUpPanelLayout.PanelState.EXPANDED) {
            super.setTaskDescriptionColor(playerFragmentColor);
        }

    }

    private void setMiniPlayerAlphaProgress(@FloatRange(from = 0, to = 1) float progress) {
        if (mMiniPlayerFragment.getView() == null) return;
        float alpha = 1 - progress;
        mMiniPlayerFragment.getView().setAlpha(alpha);
        // necessary to make the views below clickable
        mMiniPlayerFragment.getView().setVisibility(alpha == 0 ? View.GONE : View.VISIBLE);
    }

    public SlidingUpPanelLayout.PanelState getPanelState() {
        return mSlidingUpPanelLayout == null ? null : mSlidingUpPanelLayout.getPanelState();
    }

    public void collapsePanel() {
        mSlidingUpPanelLayout.setPanelState(SlidingUpPanelLayout.PanelState.COLLAPSED);
    }

    public void expandPanel() {
        mSlidingUpPanelLayout.setPanelState(SlidingUpPanelLayout.PanelState.EXPANDED);
    }

    public void hideBottomBar(final boolean hide) {
        if (hide) {
            mSlidingUpPanelLayout.setPanelHeight(0);
            collapsePanel();
        } else {
            if (!MusicPlayerRemote.getPlayingQueue().isEmpty())
                if (mBottomNavigationView.getVisibility() == View.VISIBLE) {
                    mSlidingUpPanelLayout.setPanelHeight(getResources().getDimensionPixelSize(R.dimen.mini_player_height_expanded));
                } else {
                    mSlidingUpPanelLayout.setPanelHeight(getResources().getDimensionPixelSize(R.dimen.mini_player_height));
                }
        }
    }

    public void setBottomBarVisibility(int gone) {
        if (mBottomNavigationView != null) {
            TransitionManager.beginDelayedTransition(mBottomNavigationView);
            mBottomNavigationView.setVisibility(gone);
            hideBottomBar(false);
        }
    }

    protected View wrapSlidingMusicPanel(@LayoutRes int resId) {
        @SuppressLint("InflateParams")
        View slidingMusicPanelLayout = getLayoutInflater().inflate(R.layout.sliding_music_panel_layout, null);
        ViewGroup contentContainer = slidingMusicPanelLayout.findViewById(R.id.content_container);
        getLayoutInflater().inflate(resId, contentContainer);
        return slidingMusicPanelLayout;
    }

    @Override
    public void onBackPressed() {
        if (!handleBackPress())
            super.onBackPressed();
    }

    public boolean handleBackPress() {
        if (mSlidingUpPanelLayout.getPanelHeight() != 0 && mPlayerFragment.onBackPressed())
            return true;
        if (getPanelState() == SlidingUpPanelLayout.PanelState.EXPANDED) {
            collapsePanel();
            return true;
        }
        return false;
    }


    private void animateNavigationBarColor(int color) {

        if (navigationBarColorAnimator != null) navigationBarColorAnimator.cancel();
        navigationBarColorAnimator = ValueAnimator.ofArgb(getWindow().getNavigationBarColor(), color).setDuration(ViewUtil.RETRO_MUSIC_ANIM_TIME);
        navigationBarColorAnimator.setInterpolator(new PathInterpolator(0.4f, 0f, 1f, 1f));
        navigationBarColorAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                int playerFragmentColorDark = ColorUtil.darkenColor((Integer) animation.getAnimatedValue());

                AbsSlidingMusicPanelActivity.super.setNavigationbarColor(playerFragmentColorDark);
                mBottomNavigationView.setBackgroundColor(playerFragmentColorDark);
                mMiniPlayerFragment.setColor(playerFragmentColorDark);
                    /*View view = getWindow().getDecorView();
                    view.setBackgroundColor(playerFragmentColorDark);
                    view.findViewById(R.id.toolbar).setBackgroundColor(playerFragmentColorDark);
                    view.findViewById(R.id.appbar).setBackgroundColor(playerFragmentColorDark);
                    if (view.findViewById(R.id.status_bar) != null) {
                        view.findViewById(R.id.status_bar).setBackgroundColor(ColorUtil.darkenColor(playerFragmentColorDark));
                    }*/
            }
        });
        navigationBarColorAnimator.start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (navigationBarColorAnimator != null) navigationBarColorAnimator.cancel(); // just in case
    }

    @Override
    public void setLightStatusbar(boolean enabled) {
        mLightStatusbar = enabled;
        if (getPanelState() == SlidingUpPanelLayout.PanelState.COLLAPSED) {
            super.setLightStatusbar(enabled);
        }
    }

    @Override
    public void setTaskDescriptionColor(@ColorInt int color) {
        mTaskColor = color;
        if (getPanelState() == null || getPanelState() == SlidingUpPanelLayout.PanelState.COLLAPSED) {
            super.setTaskDescriptionColor(color);
        }
    }

    @Override
    public void setNavigationbarColor(int color) {
        mNavigationbarColor = color;
        if (getPanelState() == SlidingUpPanelLayout.PanelState.COLLAPSED) {
            if (navigationBarColorAnimator != null) navigationBarColorAnimator.cancel();
            super.setNavigationbarColor(color);
        }
    }

    @Override
    protected View getSnackBarContainer() {
        return findViewById(R.id.content_container);
    }

    public SlidingUpPanelLayout getSlidingUpPanelLayout() {
        return mSlidingUpPanelLayout;
    }

    public MiniPlayerFragment getMiniPlayerFragment() {
        return mMiniPlayerFragment;
    }

    public AbsPlayerFragment getPlayerFragment() {
        return mPlayerFragment;
    }

    public BottomNavigationView getBottomNavigationView() {
        return mBottomNavigationView;
    }
}
