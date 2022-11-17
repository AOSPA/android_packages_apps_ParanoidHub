package co.aospa.hub.components;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Objects;

import co.aospa.hub.client.ClientConnector;

public class ComponentBuilder {

    private static final String TAG = "ComponentBuilder";

    public static final String COMPONENT_CONFIG = "ota_configuration";
    public static final String COMPONENT_CHANGELOG = "changelog";
    public static final String COMPONENT_UPDATES = "updates";

    private static Component buildComponentForTask(JSONObject object, int task) throws JSONException {
        Component component;
        switch (task){
            case ClientConnector.TASK_UPDATES:
                component = buildUpdateComponent(object);
                break;
            case ClientConnector.TASK_CHANGELOG:
                component = buildChangelogComponent(object);
                break;
            default:
                component = buildOtaComponent(object);
                break;
        }
        return component;
    }

    public static Component buildComponent(File data, int task) {
        Component component = null;
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new FileReader(data))) {
            for (String line; (line = br.readLine()) != null;) {
                sb.append(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        JSONObject obj = null;
        try {
            obj = new JSONObject(sb.toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }
        JSONArray taskData = null;
        try {
            if (obj != null) {
                taskData = obj.getJSONArray(getComponentForTask(task));
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        if (taskData != null) {
            for (int i = 0; i < taskData.length(); i++) {
                if (taskData.isNull(i)) {
                    continue;
                }
                try {
                    component = buildComponentForTask(taskData.getJSONObject(0), task);
                } catch (JSONException e) {
                    Log.e(TAG, "Could not parse update object, index=" + i, e);
                }
            }
        }
        return component;
    }

    private static Component buildOtaComponent(JSONObject object) throws JSONException {
        OtaConfigComponent component = new OtaConfigComponent();
        component.setOtaEnabled(object.getString("enabled"));
        component.setOtaWhitelistOnly(object.getString("whitelist_only"));
        return component;
    }

    private static Component buildChangelogComponent(JSONObject object) throws JSONException {
        ChangelogComponent component = new ChangelogComponent();
        component.setChangelog(object.getString("changelog_main"));
        return buildSharedComponent(component, object);
    }

    private static Component buildUpdateComponent(JSONObject object) throws JSONException {
        UpdateComponent component = new UpdateComponent();
        component.setFileName(object.getString("filename"));
        component.setTimestamp(object.getLong("datetime"));
        component.setFileSize(object.getLong("size"));
        component.setDownloadUrl(object.getString("url"));
        component.setAndroidVersion(object.getString("android_version"));
        component.setAndroidSpl(object.getString("android_spl"));
        return buildSharedComponent(component, object);
    }

    private static Component buildSharedComponent(
            Component component, JSONObject object) throws JSONException {
        component.setVersion(object.getString("version"));
        component.setVersionNumber(object.getString("version_code"));
        component.setBuildType(object.getString("build_type"));
        component.setId(object.getString("id"));
        return component;
    }

    public static String getComponentForTask(int task) {
        String componentTask;
        if (task == ClientConnector.TASK_UPDATES) {
            componentTask = COMPONENT_UPDATES;
        } else if (task == ClientConnector.TASK_CHANGELOG) {
            componentTask = COMPONENT_CHANGELOG;
        } else {
            componentTask = COMPONENT_CONFIG;
        }
        return componentTask;
    }

    public static int getTaskForComponent(String component) {
        int task;
        if (Objects.equals(component, COMPONENT_UPDATES)) {
            task = ClientConnector.TASK_UPDATES;
        } else if (Objects.equals(component, COMPONENT_CHANGELOG)) {
            task = ClientConnector.TASK_CHANGELOG;
        } else {
            task = ClientConnector.TASK_CONFIG;
        }
        return task;
    }
}
