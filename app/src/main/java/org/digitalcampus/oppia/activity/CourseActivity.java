/*
 * This file is part of OppiaMobile - https://digital-campus.org/
 *
 * OppiaMobile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * OppiaMobile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with OppiaMobile. If not, see <http://www.gnu.org/licenses/>.
 */

package org.digitalcampus.oppia.activity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.BitmapDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.OnInitListener;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.appcompat.app.ActionBar;
import androidx.fragment.app.Fragment;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.tabs.TabLayout;

import org.digitalcampus.mobile.learning.R;
import org.digitalcampus.oppia.adapter.ActivityPagerAdapter;
import org.digitalcampus.oppia.database.DbHelper;
import org.digitalcampus.oppia.application.App;
import org.digitalcampus.oppia.application.SessionManager;
import org.digitalcampus.oppia.model.Activity;
import org.digitalcampus.oppia.model.Course;
import org.digitalcampus.oppia.model.Lang;
import org.digitalcampus.oppia.model.MultiLangInfoModel;
import org.digitalcampus.oppia.model.Section;
import org.digitalcampus.oppia.utils.ImageUtils;
import org.digitalcampus.oppia.utils.UIUtils;
import org.digitalcampus.oppia.widgets.FeedbackWidget;
import org.digitalcampus.oppia.widgets.PageWidget;
import org.digitalcampus.oppia.widgets.QuizWidget;
import org.digitalcampus.oppia.widgets.ResourceWidget;
import org.digitalcampus.oppia.widgets.UrlWidget;
import org.digitalcampus.oppia.widgets.WidgetFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;

public class CourseActivity extends AppActivity implements OnInitListener, TabLayout.OnTabSelectedListener {

    public static final String BASELINE_TAG = "BASELINE";
    public static final String NUM_ACTIVITY_TAG = "num_activity";

    private Section section;
    private Course course;

    private int currentActivityNo = 0;

    private SharedPreferences prefs;
    private ArrayList<Activity> activities;
    private boolean isBaseline = false;
    private long userID;

    private static int ttsCheck = 0;
    private static TextToSpeech myTTS;
    private boolean ttsRunning = false;

    TabLayout tabs;
    private ViewPager viewPager;
    private ActivityPagerAdapter apAdapter;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_course);
        ActionBar actionBar = getSupportActionBar();
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        viewPager = findViewById(R.id.activity_widget_pager);

        Bundle bundle = this.getIntent().getExtras();
        if (bundle != null) {
            section = (Section) bundle.getSerializable(Section.TAG);
            course = (Course) bundle.getSerializable(Course.TAG);

            activities = section.getActivities();
            currentActivityNo = bundle.getInt(NUM_ACTIVITY_TAG);
            if (bundle.getSerializable(CourseActivity.BASELINE_TAG) != null) {
                this.isBaseline = bundle.getBoolean(CourseActivity.BASELINE_TAG);
            }
            // set image
            if (actionBar != null) {
                BitmapDrawable bm = ImageUtils.loadBMPsdcard(course.getImageFileFromRoot(), this.getResources(), App.APP_LOGO);
                actionBar.setHomeAsUpIndicator(bm);
                actionBar.setDisplayShowHomeEnabled(true);
                actionBar.setDisplayHomeAsUpEnabled(true);
                actionBar.setDisplayShowTitleEnabled(true);
            }
        }
        tabs = findViewById(R.id.tabs_toolbar);

        loadActivities();
    }

    @Override
    public void onStart() {
        super.onStart();
        initialize();
        viewPager.addOnPageChangeListener(new TabLayout.TabLayoutOnPageChangeListener(tabs));
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("currentActivityNo", currentActivityNo);
    }

    @Override
    public void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        currentActivityNo = savedInstanceState.getInt("currentActivityNo");
    }

    @Override
    public void onPause() {
        super.onPause();
        if (myTTS != null) {
            myTTS.shutdown();
            myTTS = null;
        }
        WidgetFactory currentWidget = (WidgetFactory) apAdapter.getItem(currentActivityNo);
        currentWidget.pauseTimeTracking();
        currentWidget.saveTracker();
    }

    public void onResume() {
        super.onResume();
        WidgetFactory currentWidget = (WidgetFactory) apAdapter.getItem(currentActivityNo);
        currentWidget.resumeTimeTracking();

        DbHelper db = DbHelper.getInstance(this);
        userID = db.getUserId(SessionManager.getUsername(this));
    }

    @Override
    protected void onDestroy() {
        if (myTTS != null) {
            myTTS.shutdown();
            myTTS = null;
        }
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_course, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem item = menu.findItem(R.id.menu_tts);
        if (ttsRunning) {
            item.setTitle(R.string.menu_stop_read_aloud);
        } else {
            item.setTitle(R.string.menu_read_aloud);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        Bundle tb = new Bundle();
        Intent i;
        int itemId = item.getItemId();
        if (itemId == R.id.menu_language) {
            createLanguageDialog();
            return true;
        } else if (itemId == R.id.menu_help) {
            i = new Intent(this, AboutActivity.class);
            tb.putSerializable(AboutActivity.TAB_ACTIVE, AboutActivity.TAB_HELP);
            i.putExtras(tb);
            startActivity(i);
            return true;
        } else if (itemId == android.R.id.home) {
            this.finish();
            return true;
        } else if (itemId == R.id.menu_scorecard) {
            i = new Intent(this, ScorecardActivity.class);
            tb.putSerializable(Course.TAG, course);
            i.putExtras(tb);
            startActivity(i);
            return true;
        } else if (itemId == R.id.menu_tts) {
            if (myTTS == null && !ttsRunning) {
                // check for TTS data
                Intent checkTTSIntent = new Intent();
                checkTTSIntent.setAction(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA);
                startActivityForResult(checkTTSIntent, ttsCheck);
            } else if (myTTS != null && ttsRunning) {
                this.stopReading();
            } else {
                // TTS not installed so show message
                Toast.makeText(this, this.getString(R.string.error_tts_start), Toast.LENGTH_LONG).show();
            }
            supportInvalidateOptionsMenu();
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    private void loadActivities() {
        String currentLang = prefs.getString(PrefsActivity.PREF_LANGUAGE, Locale.getDefault().getLanguage());
        String actionBarTitle = section.getTitle(currentLang);
        if (actionBarTitle != null && !actionBarTitle.equals(MultiLangInfoModel.DEFAULT_NOTITLE)) {
            setTitle(actionBarTitle);
        } else {
            ArrayList<Activity> sectionActivities = section.getActivities();
            String preTestTitle = getString(R.string.alert_pretest);
            setTitle(!sectionActivities.isEmpty() && sectionActivities.get(0).getTitle(currentLang).equalsIgnoreCase(preTestTitle) ?
                    preTestTitle : isBaseline ? getString(R.string.title_baseline) : "");
        }

        List<Fragment> fragments = new ArrayList<>();
        List<String> titles = new ArrayList<>();

        for (int i = 0; i < activities.size(); i++) {
            Activity activity = activities.get(i);
            //Fragment creation
            if (activity.getActType().equalsIgnoreCase("page")) {
                fragments.add(PageWidget.newInstance(activity, course, isBaseline));
            } else if (activity.getActType().equalsIgnoreCase("quiz")) {
                QuizWidget newQuiz = QuizWidget.newInstance(activity, course, isBaseline);
                if (apAdapter != null) {
                    //If there was a previous quizWidget, we apply its current config to the new one
                    QuizWidget previousQuiz = (QuizWidget) apAdapter.getItem(i);
                    newQuiz.setWidgetConfig(previousQuiz.getWidgetConfig());
                }
                fragments.add(newQuiz);
            } else if (activity.getActType().equalsIgnoreCase("resource")) {
                fragments.add(ResourceWidget.newInstance(activity, course, isBaseline));
            } else if (activity.getActType().equalsIgnoreCase("feedback")) {
                FeedbackWidget newFeedback = FeedbackWidget.newInstance(activity, course, isBaseline);
                if (apAdapter != null) {
                    //If there was a previous feedbackWidget, we apply its current config to the new one
                    FeedbackWidget previousWidget = (FeedbackWidget) apAdapter.getItem(i);
                    newFeedback.setWidgetConfig(previousWidget.getWidgetConfig());
                }
                fragments.add(newFeedback);
            } else if (activities.get(i).getActType().equalsIgnoreCase("url")) {
                UrlWidget f = UrlWidget.newInstance(activities.get(i), course, isBaseline);
                fragments.add(f);
            }
            titles.add(activity.getTitle(currentLang));
        }

        apAdapter = new ActivityPagerAdapter(this, getSupportFragmentManager(), fragments, titles);
        viewPager.setAdapter(apAdapter);
        tabs.setupWithViewPager(viewPager);
        tabs.setTabMode(activities.size() > 1 ? TabLayout.MODE_SCROLLABLE : TabLayout.MODE_FIXED);
        tabs.addOnTabSelectedListener(this);
        apAdapter.updateTabViews(tabs);
        viewPager.setCurrentItem(currentActivityNo);
    }

    private void createLanguageDialog() {
        UIUtils.createLanguageDialog(this, (ArrayList<Lang>) course.getLangs(), prefs, new Callable<Boolean>() {
            public Boolean call() {
                CourseActivity.this.loadActivities();
                return true;
            }
        });
    }

    @Override
    public void onTabSelected(TabLayout.Tab tab) {
        int tabSelected = tab.getPosition();
        Log.d(TAG, "Tab selected " + tabSelected + " current act " + currentActivityNo);

        if (canNavigateTo(tabSelected)) {
            viewPager.setCurrentItem(tabSelected);
            currentActivityNo = tabSelected;
            this.stopReading();
            WidgetFactory currentWidget = (WidgetFactory) apAdapter.getItem(currentActivityNo);
            currentWidget.resetTimeTracking();
        } else {
            Runnable setPreviousTab = new Runnable() {
                @Override
                public void run() {
                    UIUtils.showAlert(CourseActivity.this, R.string.sequencing_dialog_title, R.string.sequencing_section_message);
                    TabLayout.Tab target = tabs.getTabAt(currentActivityNo);
                    if (target != null) {
                        target.select();
                    }
                }
            };
            new Handler().post(setPreviousTab);
        }
    }

    @Override
    public void onTabUnselected(TabLayout.Tab tab) {
        ((WidgetFactory) apAdapter.getItem(currentActivityNo)).saveTracker();
    }

    @Override
    public void onTabReselected(TabLayout.Tab tab) {
        int tabSelected = tab.getPosition();
        Log.d(TAG, "Tab selected " + tabSelected + " current act " + currentActivityNo);

        WidgetFactory currentWidget = (WidgetFactory) apAdapter.getItem(currentActivityNo);
        currentWidget.resetTimeTracking();
    }


    private boolean canNavigateTo(int newTab) {
        //If the course does not have a sequencing mode, we can navigate freely
        if (course.getSequencingMode().equals(Course.SEQUENCING_MODE_NONE)) return true;
        // if it is a previous activity (or the first), no need for further checks
        if ((newTab == 0) || (newTab <= currentActivityNo)) return true;
        Activity previousActivity = activities.get(newTab - 1);
        //the user can navigate to the activity if its directly preceding one is completed
        DbHelper db = DbHelper.getInstance(this);
        return db.activityCompleted(course.getCourseId(), previousActivity.getDigest(), userID);
    }

    public void onInit(int status) {
        // check for successful instantiation
        if (status == TextToSpeech.SUCCESS) {
            ttsRunning = true;
            ((WidgetFactory) apAdapter.getItem(currentActivityNo)).setReadAloud(true);
            supportInvalidateOptionsMenu();
            HashMap<String, String> params = new HashMap<>();
            params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, TAG);
            myTTS.speak(((WidgetFactory) apAdapter.getItem(currentActivityNo)).getContentToRead(), TextToSpeech.QUEUE_FLUSH, params);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1) {
                myTTS.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override
                    public void onDone(String utteranceId) {
                        CourseActivity.this.ttsRunning = false;
                        myTTS = null;
                    }

                    @Override
                    public void onError(String utteranceId) {
                        // does not need completing
                    }

                    @Override
                    public void onStart(String utteranceId) {
                        // does not need completing
                    }
                });
            }
        } else {
            // TTS not installed so show message
            Toast.makeText(this, this.getString(R.string.error_tts_start), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == ttsCheck && resultCode == TextToSpeech.Engine.CHECK_VOICE_DATA_PASS) {
            // the user has the necessary data - create the TTS
            myTTS = new TextToSpeech(this, this);
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void stopReading() {
        if (myTTS != null) {
            myTTS.stop();
            myTTS = null;
        }
        this.ttsRunning = false;
    }
}
