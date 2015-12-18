package io.github.hidroh.materialistic;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.app.SearchManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.content.ShadowContentResolverCompatJellybean;
import android.support.v7.view.ActionMode;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SearchView;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.view.MenuItem;
import android.view.View;
import android.widget.PopupMenu;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.MockitoAnnotations;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.fakes.RoboMenuItem;
import org.robolectric.internal.ShadowExtractor;
import org.robolectric.res.builder.RobolectricPackageManager;
import org.robolectric.shadows.ShadowAlertDialog;
import org.robolectric.shadows.ShadowApplication;
import org.robolectric.shadows.ShadowContentResolver;
import org.robolectric.shadows.ShadowPopupMenu;
import org.robolectric.shadows.ShadowProgressDialog;
import org.robolectric.shadows.ShadowToast;
import org.robolectric.shadows.support.v4.ShadowLocalBroadcastManager;
import org.robolectric.util.ActivityController;

import java.util.Set;

import javax.inject.Inject;

import io.github.hidroh.materialistic.accounts.UserServices;
import io.github.hidroh.materialistic.data.FavoriteManager;
import io.github.hidroh.materialistic.data.MaterialisticProvider;
import io.github.hidroh.materialistic.test.ShadowItemTouchHelper;
import io.github.hidroh.materialistic.test.ShadowRecyclerView;
import io.github.hidroh.materialistic.test.ShadowRecyclerViewAdapter;
import io.github.hidroh.materialistic.test.TestFavoriteActivity;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertTrue;
import static org.assertj.android.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.robolectric.Shadows.shadowOf;
import static org.robolectric.shadows.support.v4.Shadows.shadowOf;

@Config(shadows = {ShadowContentResolverCompatJellybean.class, ShadowRecyclerViewAdapter.class, ShadowRecyclerViewAdapter.ShadowViewHolder.class, ShadowRecyclerView.class, ShadowItemTouchHelper.class})
@RunWith(RobolectricGradleTestRunner.class)
public class FavoriteActivityTest {
    private ActivityController<TestFavoriteActivity> controller;
    private TestFavoriteActivity activity;
    private RecyclerView.Adapter adapter;
    private RecyclerView recyclerView;
    private Fragment fragment;
    @Inject FavoriteManager favoriteManager;
    @Inject ActionViewResolver actionViewResolver;
    @Inject UserServices userServices;
    @Captor ArgumentCaptor<Set<String>> selection;
    @Captor ArgumentCaptor<View.OnClickListener> searchViewClickListener;
    @Captor ArgumentCaptor<SearchView.OnCloseListener> searchViewCloseListener;
    @Captor ArgumentCaptor<UserServices.Callback> userServicesCallback;
    private ShadowContentResolver resolver;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        TestApplication.applicationGraph.inject(this);
        reset(favoriteManager);
        reset(userServices);
        reset(actionViewResolver.getActionView(mock(MenuItem.class)));
        controller = Robolectric.buildActivity(TestFavoriteActivity.class);
        resolver = shadowOf(ShadowApplication.getInstance().getContentResolver());
        ContentValues cv = new ContentValues();
        cv.put("itemid", "1");
        cv.put("title", "title");
        cv.put("url", "http://example.com");
        cv.put("time", String.valueOf(System.currentTimeMillis()));
        resolver.insert(MaterialisticProvider.URI_FAVORITE, cv);
        cv = new ContentValues();
        cv.put("itemid", "2");
        cv.put("title", "ask HN");
        cv.put("url", "http://example.com");
        cv.put("time", String.valueOf(System.currentTimeMillis()));
        resolver.insert(MaterialisticProvider.URI_FAVORITE, cv);
        activity = controller.create().postCreate(null).start().resume().visible().get(); // skip menu due to search view
        recyclerView = (RecyclerView) activity.findViewById(R.id.recycler_view);
        adapter = recyclerView.getAdapter();
        fragment = activity.getSupportFragmentManager().findFragmentById(android.R.id.list);
    }

    @Test
    public void testOptionsMenuClear() {
        assertTrue(shadowOf(activity).getOptionsMenu().findItem(R.id.menu_clear).isVisible());
        shadowOf(activity).clickMenuItem(R.id.menu_clear);
        AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
        dialog.getButton(DialogInterface.BUTTON_NEGATIVE).performClick();
        assertEquals(2, adapter.getItemCount());

        shadowOf(activity).clickMenuItem(R.id.menu_clear);
        dialog = ShadowAlertDialog.getLatestAlertDialog();
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
        assertEquals(0, adapter.getItemCount());
    }

    @Test
    public void testSearchView() {
        SearchView searchView = (SearchView) actionViewResolver.getActionView(mock(MenuItem.class));
        verify(searchView, atLeastOnce()).setOnSearchClickListener(searchViewClickListener.capture());
        verify(searchView, atLeastOnce()).setOnCloseListener(searchViewCloseListener.capture());

        View.OnClickListener clickListener =
                searchViewClickListener.getAllValues().get(searchViewClickListener.getAllValues().size() - 1);
        clickListener.onClick(searchView);
        assertFalse(shadowOf(activity).getOptionsMenu().findItem(R.id.menu_clear).isVisible());

        SearchView.OnCloseListener closeListener =
                searchViewCloseListener.getAllValues().get(searchViewCloseListener.getAllValues().size() - 1);
        closeListener.onClose();
        assertTrue(shadowOf(activity).getOptionsMenu().findItem(R.id.menu_clear).isVisible());

        assertEquals(2, adapter.getItemCount());
        ((FavoriteFragment) fragment).filter("ask");
        assertEquals(1, adapter.getItemCount());
        closeListener.onClose();
        assertEquals(2, adapter.getItemCount());
    }

    @Test
    public void testItemClick() {
        assertEquals(2, adapter.getItemCount());
        ShadowRecyclerViewAdapter shadowAdapter = (ShadowRecyclerViewAdapter) ShadowExtractor
                .extract(adapter);
        shadowAdapter.makeItemVisible(0);
        RecyclerView.ViewHolder holder = shadowAdapter.getViewHolder(0);
        holder.itemView.performClick();
        assertNotNull(shadowOf(activity).getNextStartedActivity());
    }

    @Test
    public void testActionMode() {
        ShadowRecyclerViewAdapter shadowAdapter = (ShadowRecyclerViewAdapter) ShadowExtractor
                .extract(adapter);
        shadowAdapter.makeItemVisible(0);
        RecyclerView.ViewHolder holder = shadowAdapter.getViewHolder(0);
        holder.itemView.performLongClick();
        assertNotNull(activity.actionModeCallback);
    }

    @Test
    public void testSelectionToggle() {
        ShadowRecyclerViewAdapter shadowAdapter = (ShadowRecyclerViewAdapter) ShadowExtractor
                .extract(adapter);
        shadowAdapter.makeItemVisible(0);
        RecyclerView.ViewHolder holder = shadowAdapter.getViewHolder(0);
        holder.itemView.performLongClick();
        holder.itemView.performClick();
        assertNull(shadowOf(activity).getNextStartedActivity());
        holder.itemView.performClick();
        assertNull(shadowOf(activity).getNextStartedActivity());
    }

    @Test
    public void testDelete() {
        ShadowRecyclerViewAdapter shadowAdapter = (ShadowRecyclerViewAdapter) ShadowExtractor
                .extract(adapter);
        shadowAdapter.makeItemVisible(0);
        RecyclerView.ViewHolder holder = shadowAdapter.getViewHolder(0);
        holder.itemView.performLongClick();

        ActionMode actionMode = mock(ActionMode.class);
        activity.actionModeCallback.onActionItemClicked(actionMode, new RoboMenuItem(R.id.menu_clear));
        AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
        dialog.getButton(DialogInterface.BUTTON_NEGATIVE).performClick();
        assertEquals(2, adapter.getItemCount());

        activity.actionModeCallback.onActionItemClicked(actionMode, new RoboMenuItem(R.id.menu_clear));
        dialog = ShadowAlertDialog.getLatestAlertDialog();
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
        verify(favoriteManager).remove(any(Context.class), selection.capture());
        assertThat(selection.getValue()).contains("2"); // sort by date desc
        verify(actionMode).finish();

        resolver.delete(MaterialisticProvider.URI_FAVORITE, "itemid=?", new String[]{"2"});
        ShadowLocalBroadcastManager manager = shadowOf(LocalBroadcastManager.getInstance(activity));
        manager.getRegisteredBroadcastReceivers().get(0).broadcastReceiver
                .onReceive(activity, new Intent(FavoriteManager.ACTION_CLEAR));
        assertEquals(1, adapter.getItemCount());
    }

    @Test
    public void testSwipeToDelete() {
        ShadowRecyclerViewAdapter shadowAdapter = (ShadowRecyclerViewAdapter) ShadowExtractor
                .extract(adapter);
        shadowAdapter.makeItemVisible(0);
        RecyclerView.ViewHolder holder = shadowAdapter.getViewHolder(0);
        ((ShadowRecyclerView) ShadowExtractor.extract(recyclerView)).getItemTouchHelperCallback()
                .onSwiped(holder, ItemTouchHelper.LEFT);
        verify(favoriteManager).remove(any(Context.class), eq("2"));
        resolver.delete(MaterialisticProvider.URI_FAVORITE, "itemid=?", new String[]{"2"});
        ShadowLocalBroadcastManager manager = shadowOf(LocalBroadcastManager.getInstance(activity));
        manager.getRegisteredBroadcastReceivers().get(0).broadcastReceiver
                .onReceive(activity, new Intent(FavoriteManager.ACTION_REMOVE));
        assertEquals(1, adapter.getItemCount());
    }

    @Test
    public void testEmail() {
        shadowOf(activity).clickMenuItem(R.id.menu_email);
        verify(favoriteManager).get(any(Context.class), anyString());
        AlertDialog progressDialog = ShadowProgressDialog.getLatestAlertDialog();
        assertThat(progressDialog).isShowing();

        ResolveInfo emailResolveInfo = new ResolveInfo();
        emailResolveInfo.activityInfo = new ActivityInfo();
        emailResolveInfo.activityInfo.applicationInfo = new ApplicationInfo();
        emailResolveInfo.activityInfo.applicationInfo.packageName =
                ListActivity.class.getPackage().getName();
        emailResolveInfo.activityInfo.name = ListActivity.class.getName();
        RobolectricPackageManager rpm = (RobolectricPackageManager) RuntimeEnvironment.application.getPackageManager();
        rpm.addResolveInfoForIntent(new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")),
                emailResolveInfo);
        ShadowLocalBroadcastManager manager = shadowOf(LocalBroadcastManager.getInstance(activity));
        Intent intent = new Intent(FavoriteManager.ACTION_GET);
        intent.putExtra(FavoriteManager.ACTION_GET_EXTRA_DATA, new FavoriteManager.Favorite[]{});
        manager.getRegisteredBroadcastReceivers().get(0).broadcastReceiver
                .onReceive(activity, intent);
        assertThat(progressDialog).isNotShowing();
        assertNotNull(shadowOf(activity).getNextStartedActivity());
    }

    @Test
    public void testFilter() {
        assertTrue(shadowOf(activity).getOptionsMenu().findItem(R.id.menu_search).isVisible());
        Intent intent = new Intent();
        intent.putExtra(SearchManager.QUERY, "blah");
        controller.newIntent(intent);
        assertEquals(0, adapter.getItemCount());
        intent = new Intent();
        intent.putExtra(SearchManager.QUERY, "ask");
        controller.newIntent(intent);
        assertEquals(1, adapter.getItemCount());
    }

    @Test
    public void testSaveState() {
        Intent intent = new Intent();
        intent.putExtra(SearchManager.QUERY, "ask");
        Bundle outState = new Bundle();
        controller.newIntent(intent).saveInstanceState(outState);
        ActivityController<TestFavoriteActivity> controller = Robolectric
                .buildActivity(TestFavoriteActivity.class)
                .create(outState)
                .postCreate(outState)
                .start()
                .resume()
                .visible();
        assertEquals(1, ((RecyclerView) controller.get().findViewById(R.id.recycler_view))
                .getAdapter().getItemCount());
        controller.pause().stop().destroy();
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    @Test
    public void testVoteItem() {
        ShadowRecyclerViewAdapter shadowAdapter = (ShadowRecyclerViewAdapter) ShadowExtractor
                .extract(adapter);
        shadowAdapter.makeItemVisible(0);
        shadowAdapter.getViewHolder(0).itemView.findViewById(R.id.button_more).performClick();
        PopupMenu popupMenu = ShadowPopupMenu.getLatestPopupMenu();
        Assert.assertNotNull(popupMenu);
        shadowOf(popupMenu).getOnMenuItemClickListener()
                .onMenuItemClick(new RoboMenuItem(R.id.menu_contextual_vote));
        verify(userServices).voteUp(any(Context.class), anyString(), userServicesCallback.capture());
        userServicesCallback.getValue().onDone(true);
        assertEquals(activity.getString(R.string.voted), ShadowToast.getTextOfLatestToast());
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    @Test
    public void testVoteItemPromptToLogin() {
        ShadowRecyclerViewAdapter shadowAdapter = (ShadowRecyclerViewAdapter) ShadowExtractor
                .extract(adapter);
        shadowAdapter.makeItemVisible(0);
        shadowAdapter.getViewHolder(0).itemView.findViewById(R.id.button_more).performClick();
        PopupMenu popupMenu = ShadowPopupMenu.getLatestPopupMenu();
        Assert.assertNotNull(popupMenu);
        shadowOf(popupMenu).getOnMenuItemClickListener()
                .onMenuItemClick(new RoboMenuItem(R.id.menu_contextual_vote));
        verify(userServices).voteUp(any(Context.class), anyString(), userServicesCallback.capture());
        userServicesCallback.getValue().onDone(false);
        assertThat(shadowOf(activity).getNextStartedActivity())
                .hasComponent(activity, LoginActivity.class);
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    @Test
    public void testVoteItemFailed() {
        ShadowRecyclerViewAdapter shadowAdapter = (ShadowRecyclerViewAdapter) ShadowExtractor
                .extract(adapter);
        shadowAdapter.makeItemVisible(0);
        shadowAdapter.getViewHolder(0).itemView.findViewById(R.id.button_more).performClick();
        PopupMenu popupMenu = ShadowPopupMenu.getLatestPopupMenu();
        Assert.assertNotNull(popupMenu);
        shadowOf(popupMenu).getOnMenuItemClickListener()
                .onMenuItemClick(new RoboMenuItem(R.id.menu_contextual_vote));
        verify(userServices).voteUp(any(Context.class), anyString(), userServicesCallback.capture());
        userServicesCallback.getValue().onError();
        assertEquals(activity.getString(R.string.vote_failed), ShadowToast.getTextOfLatestToast());
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    @Test
    public void testReply() {
        ShadowRecyclerViewAdapter shadowAdapter = (ShadowRecyclerViewAdapter) ShadowExtractor
                .extract(adapter);
        shadowAdapter.makeItemVisible(0);
        shadowAdapter.getViewHolder(0).itemView.findViewById(R.id.button_more).performClick();
        PopupMenu popupMenu = ShadowPopupMenu.getLatestPopupMenu();
        Assert.assertNotNull(popupMenu);
        shadowOf(popupMenu).getOnMenuItemClickListener()
                .onMenuItemClick(new RoboMenuItem(R.id.menu_contextual_comment));
        assertThat(shadowOf(activity).getNextStartedActivity())
                .hasComponent(activity, ComposeActivity.class);
    }

    @After
    public void tearDown() {
        controller.pause().stop().destroy();
    }
}
