package io.github.xausky.unitymodmanager.fragment;

import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.PackageParser;
import android.content.pm.ShortcutManager;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.sip.SipRegistrationListener;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.pm.ShortcutInfoCompat;
import android.support.v4.content.pm.ShortcutManagerCompat;
import android.support.v4.graphics.drawable.IconCompat;
import android.support.v7.widget.CardView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.allenliu.versionchecklib.v2.AllenVersionChecker;
import com.allenliu.versionchecklib.v2.builder.UIData;
import com.allenliu.versionchecklib.v2.callback.RequestVersionListener;
import com.lody.virtual.client.core.InstallStrategy;
import com.lody.virtual.client.core.VirtualCore;
import com.lody.virtual.remote.InstallResult;
import com.lody.virtual.remote.InstalledAppInfo;

import org.apache.commons.io.IOUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;

import javax.net.ssl.HttpsURLConnection;

import io.github.xausky.unitymodmanager.MainApplication;
import io.github.xausky.unitymodmanager.R;
import io.github.xausky.unitymodmanager.ShortcutActivity;
import io.github.xausky.unitymodmanager.dialog.ApplicationChooseDialog;
import io.github.xausky.unitymodmanager.utils.ModUtils;
import io.github.xausky.unitymodmanager.utils.NativeUtils;

/**
 * Created by xausky on 18-3-3.
 */

public class HomeFragment extends BaseFragment implements View.OnClickListener, ApplicationChooseDialog.OnApplicationChooseDialogResultListener{
    public static final String PACKAGE_PREFERENCE_KEY = "PACKAGE_PREFERENCE_KEY";
    public static final String BASE_APK_PATH_PREFERENCE_KEY = "BASE_APK_PATH_PREFERENCE_KEY";
    public static final String ALL_APPLICATION_PACKAGE_REGEX = "^.*$";
    public String packageName;
    public String apkPath;
    public String baseApkPath;
    private View view;
    private TextView summary;
    private TextView clientState;
    private TextView currentVersion;
    private String currentVersionString;
    private TextView latestVersion;
    private CardView clientStateCardView;
    private AttachFragment attachFragment;
    private VisibilityFragment visibilityFragment;
    private ModFragment modFragment;
    private Context context;
    private SharedPreferences settings;
    private ApplicationChooseDialog dialog;
    private ProgressDialog progressDialog;
    private VirtualCore va;

    @Override
    public BaseFragment setBase(Context base) {
        settings = base.getSharedPreferences(SettingFragment.SETTINGS_PREFERENCE_NAME, Context.MODE_PRIVATE);
        packageName = settings.getString(PACKAGE_PREFERENCE_KEY, null);
        baseApkPath = settings.getString(BASE_APK_PATH_PREFERENCE_KEY, null);
        return super.setBase(base);
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {
        context = inflater.getContext();
        dialog = new ApplicationChooseDialog(context, this, ALL_APPLICATION_PACKAGE_REGEX, true, true);
        dialog.setListener(this);
        progressDialog = new ProgressDialog(context);
        progressDialog.setTitle(R.string.progress_dialog_title);
        progressDialog.setMessage(getString(R.string.progress_dialog_message));
        progressDialog.setCancelable(false);
        if(view == null){
            view = inflater.inflate(R.layout.home_fragment, container, false);
            attachFragment = (AttachFragment) BaseFragment.fragment(R.id.nav_attach);
            visibilityFragment = (VisibilityFragment) BaseFragment.fragment(R.id.nav_visibility);
            modFragment = (ModFragment) BaseFragment.fragment(R.id.nav_mod);
            summary = (TextView) view.findViewById(R.id.home_summary);
            va = VirtualCore.get();
            currentVersion = (TextView) view.findViewById(R.id.home_current_version);
            latestVersion = (TextView) view.findViewById(R.id.home_latest_version);
            clientState = (TextView) view.findViewById(R.id.home_client_state);
            clientStateCardView = (CardView) view.findViewById(R.id.home_client_state_card_view);
            clientStateCardView.setOnClickListener(this);
            currentVersionString = "unknown";
            try {
                currentVersionString = "v" + context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
            }
            currentVersion.setText(String.format(getText(R.string.home_current_version).toString(), currentVersionString));
            checkVersion();
        }
        clientUpdate();
        return view;
    }

    private void checkVersion(){
        AllenVersionChecker
                .getInstance()
                .requestVersion()
                .setRequestUrl("https://api.github.com/repos/xausky/UnityModManager/releases")
                .request(new RequestVersionListener() {
                    @Nullable
                    @Override
                    public UIData onRequestVersionSuccess(String result) {
                        try {
                            JSONArray array = new JSONArray(result);
                            JSONObject latestRelease = null;
                            for(int i =0; i < array.length(); ++ i){
                                JSONObject release = array.getJSONObject(i);
                                if(!release.getBoolean("prerelease")){
                                    latestRelease = release;
                                    break;
                                }
                            }
                            if(latestRelease != null){
                                String latestVersion = latestRelease.getString("tag_name");
                                final String textViewString = String.format(context.getString(R.string.home_latest_version), latestVersion);
                                HomeFragment.this.latestVersion.setText(textViewString);
                                if(currentVersionString.indexOf('-') > 0){
                                    return null;
                                }
                                if(!currentVersionString.equals(latestVersion)){
                                    UIData data = UIData.create();
                                    data.setTitle("新版本发布:" + latestVersion);
                                    data.setContent("更新日志：\n" + latestRelease.getString("body") + "\n\n若更新失败可到B站找最新下载地址自行更新。");
                                    data.setDownloadUrl(latestRelease.getJSONArray("assets").getJSONObject(0).getString("browser_download_url"));
                                    return data;
                                } else {
                                    Drawable check = context.getResources().getDrawable(R.drawable.ic_check_circle);
                                    check.setBounds(0, 0, check.getMinimumWidth(), check.getMinimumHeight());
                                    HomeFragment.this.latestVersion.setCompoundDrawables(check, null, null, null);
                                }
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                        return null;
                    }

                    @Override
                    public void onRequestVersionFailure(String message) {

                    }
                })
                .excuteMission(context);
    }

    private void clientUpdate(){
        InstalledAppInfo installedAppInfo = va.getInstalledAppInfo(packageName, 0);
        if(installedAppInfo != null){
            String versionName = installedAppInfo.getPackageInfo(0).versionName;
            apkPath = installedAppInfo.apkPath;
            clientState.setText(String.format(getText(R.string.home_client_installed).toString(), versionName));
            clientState.setCompoundDrawablesWithIntrinsicBounds(ContextCompat.getDrawable(context,R.drawable.ic_check),null, null, null);
            InputStream mapInputStream = null;
            try {
                File map = new File(this.context.getFilesDir() + "/map.json");
                if(map.exists()){
                    mapInputStream = new FileInputStream(map);
                    byte[] bytes = new byte[mapInputStream.available()];
                    if(mapInputStream.read(bytes) == -1){
                        throw new IOException("map.json read failed.");
                    }
                    String json = new String(bytes);
                    ModUtils.map = new JSONObject(json);
                }
            } catch (IOException e) {
                e.printStackTrace();
            } catch (JSONException e) {
                e.printStackTrace();
            }finally {
                if(mapInputStream != null){
                    try {
                        mapInputStream.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        } else {
            clientState.setText(getText(R.string.home_client_uninstalled));
            clientState.setCompoundDrawablesWithIntrinsicBounds(ContextCompat.getDrawable(context,R.drawable.ic_clear),null, null, null);
        }
        String summaryString = String.format(getString(R.string.home_summary_context),
                modFragment.getEnableItemCount(),
                modFragment.getItemCount(),
                attachFragment.getItemCount(),
                visibilityFragment.getItemCount(),
                va.getInstalledAppCount());
        summary.setText(summaryString);
    }

    @Override
    public void onClick(View v) {
        dialog.show();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        dialog.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onDestroyView() {
        dialog.dismiss();
        super.onDestroyView();
    }

    private void clientInstall(final String apkPath){
        progressDialog.show();
        new Thread(){
            @Override
            public void run() {
                Log.d(MainApplication.LOG_TAG, apkPath);
                final String resultString;
                if(NativeUtils.GenerateMapFile(apkPath, HomeFragment.this.context.getFilesDir().getAbsolutePath() + "/map.json") == NativeUtils.RESULT_STATE_OK){
                    final InstallResult result = VirtualCore.get().installPackage(apkPath, InstallStrategy.UPDATE_IF_EXIST);
                    if(result.isSuccess){
                        if(modFragment.getEnableItemCount() > 0){
                            modFragment.setNeedPatch(true);
                        }
                        HomeFragment.this.packageName = result.packageName;
                        HomeFragment.this.baseApkPath = apkPath;
                        InstalledAppInfo info = VirtualCore.get().getInstalledAppInfo(HomeFragment.this.packageName, 0);
                        HomeFragment.this.apkPath = info.apkPath;
                        settings.edit()
                                .putString(PACKAGE_PREFERENCE_KEY, HomeFragment.this.packageName)
                                .putString(BASE_APK_PATH_PREFERENCE_KEY, HomeFragment.this.baseApkPath)
                                .apply();
                        resultString = "安装成功，可以在设置界面创建快捷方式。";
                    } else {
                        resultString = result.error;
                    }
                } else {
                    resultString = "生成映射文件失败。";
                }
                progressDialog.dismiss();
                HomeFragment.this.view.post(new Runnable() {
                    @Override
                    public void run() {
                        clientUpdate();
                        Toast.makeText(context, resultString, Toast.LENGTH_LONG).show();
                    }
                });
            }
        }.start();
    }

    public void crateShortcut(InstalledAppInfo info){
        if (ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
            PackageManager manager = va.getPackageManager();
            String name = manager.getApplicationLabel(info.getApplicationInfo(0)) + "[模组管理器]";
            BitmapDrawable icon = (BitmapDrawable)manager.getApplicationIcon(info.getApplicationInfo(0));
            Intent shortcutInfoIntent = new Intent(Intent.ACTION_VIEW);
            shortcutInfoIntent.setClass(context, ShortcutActivity.class);
            shortcutInfoIntent.putExtra("io.github.xausky.unitymodmanager.launchPackage", info.packageName);
            ShortcutInfoCompat shortcutInfo = new ShortcutInfoCompat.Builder(context, name)
                    .setIcon(IconCompat.createWithBitmap(icon.getBitmap()))
                    .setShortLabel(name)
                    .setIntent(shortcutInfoIntent)
                    .build();
            ShortcutManagerCompat.requestPinShortcut(context, shortcutInfo, null);
        }
    }

    @Override
    public void OnApplicationChooseDialogResult(String packageName, String apkPath) {
        clientInstall(apkPath);
        dialog.hide();
    }
}
