package io.mrarm.irc.setting;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import java.io.IOException;
import java.io.InputStream;

import io.mrarm.irc.R;
import io.mrarm.irc.config.ChatBackgroundSettings;
import io.mrarm.irc.dialog.MaterialColorPickerDialog;

/** Single Interface setting that owns color/image selection and image fitting. */
public class ChatBackgroundSetting extends SimpleSetting
        implements SettingsListAdapter.ActivityResultCallback {

    private static final int sHolder = SettingsListAdapter.registerViewHolder(Holder.class,
            R.layout.settings_list_entry);

    private final int requestCode;
    private final Context context;

    public ChatBackgroundSetting(SettingsListAdapter adapter, String name) {
        super(name, null);
        context = adapter.getActivity().getApplicationContext();
        requestCode = adapter.getRequestCodeCounter().next();
        refreshDescription();
    }

    @Override
    public int getViewHolder() {
        return sHolder;
    }

    public void refreshDescription() {
        if (ChatBackgroundSettings.TYPE_IMAGE.equals(ChatBackgroundSettings.getType(context))
                && ChatBackgroundSettings.hasImage(context)) {
            mValue = context.getString(R.string.chat_background_desc_image,
                    scaleLabel(context, ChatBackgroundSettings.getScale(context)));
        } else {
            String color = ChatBackgroundSettings.hasCustomColor(context)
                    ? String.format("#%06X", ChatBackgroundSettings.getCustomColor(context,
                    Color.TRANSPARENT) & 0xFFFFFF)
                    : context.getString(R.string.chat_background_theme_color);
            mValue = context.getString(R.string.chat_background_desc_color, color);
        }
        onUpdated();
    }

    private void showMenu(View view, SettingsListAdapter adapter) {
        boolean image = ChatBackgroundSettings.TYPE_IMAGE.equals(
                ChatBackgroundSettings.getType(view.getContext()))
                && ChatBackgroundSettings.hasImage(view.getContext());
        CharSequence[] items = image
                ? new CharSequence[] {
                        view.getContext().getString(R.string.chat_background_color),
                        view.getContext().getString(R.string.chat_background_choose_image),
                        view.getContext().getString(R.string.chat_background_image_fitting)
                }
                : new CharSequence[] {
                        view.getContext().getString(R.string.chat_background_color),
                        view.getContext().getString(R.string.chat_background_choose_image)
                };
        new AlertDialog.Builder(view.getContext())
                .setTitle(R.string.pref_title_chat_background)
                .setItems(items, (dialog, which) -> {
                    if (which == 0)
                        showColorMenu(view.getContext());
                    else if (which == 1)
                        chooseImage(adapter);
                    else
                        showScaleMenu(view.getContext());
                })
                .show();
    }

    private void showColorMenu(Context context) {
        CharSequence[] items = {
                context.getString(R.string.chat_background_theme_color),
                context.getString(R.string.chat_background_custom_color)
        };
        new AlertDialog.Builder(context)
                .setTitle(R.string.chat_background_color)
                .setItems(items, (dialog, which) -> {
                    if (which == 0) {
                        ChatBackgroundSettings.useThemeColor(context);
                        refreshDescription();
                    } else {
                        MaterialColorPickerDialog picker = new MaterialColorPickerDialog(context);
                        picker.setTitle(context.getString(R.string.chat_background_color));
                        picker.setColorPickListener(color -> {
                            ChatBackgroundSettings.useColor(context, color);
                            refreshDescription();
                        });
                        picker.show();
                    }
                })
                .show();
    }

    private void chooseImage(SettingsListAdapter adapter) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        adapter.launchActivityForResult(intent, requestCode);
    }

    private void showScaleMenu(Context context) {
        CharSequence[] items = {
                context.getString(R.string.chat_background_scale_fill),
                context.getString(R.string.chat_background_scale_fit),
                context.getString(R.string.chat_background_scale_stretch)
        };
        String current = ChatBackgroundSettings.getScale(context);
        int checked = ChatBackgroundSettings.SCALE_FIT.equals(current) ? 1
                : ChatBackgroundSettings.SCALE_STRETCH.equals(current) ? 2 : 0;
        new AlertDialog.Builder(context)
                .setTitle(R.string.chat_background_image_fitting)
                .setSingleChoiceItems(items, checked, (dialog, which) -> {
                    ChatBackgroundSettings.setScale(context,
                            which == 1 ? ChatBackgroundSettings.SCALE_FIT
                                    : which == 2 ? ChatBackgroundSettings.SCALE_STRETCH
                                    : ChatBackgroundSettings.SCALE_FILL);
                    refreshDescription();
                    dialog.dismiss();
                })
                .show();
    }

    @Override
    public void onSettingsActivityResult(Activity activity, int requestCode, int resultCode,
                                         Intent data) {
        if (this.requestCode != requestCode || resultCode != Activity.RESULT_OK || data == null)
            return;
        Uri uri = data.getData();
        if (uri == null)
            return;
        try (InputStream input = activity.getContentResolver().openInputStream(uri)) {
            if (input == null)
                throw new IOException("Unable to open selected image");
            ChatBackgroundSettings.storeImage(activity, input);
            refreshDescription();
            showScaleMenu(activity);
        } catch (IOException e) {
            Toast.makeText(activity, R.string.error_file_open, Toast.LENGTH_SHORT).show();
        }
    }

    private static CharSequence scaleLabel(Context context, String scale) {
        if (ChatBackgroundSettings.SCALE_FIT.equals(scale))
            return context.getString(R.string.chat_background_scale_fit);
        if (ChatBackgroundSettings.SCALE_STRETCH.equals(scale))
            return context.getString(R.string.chat_background_scale_stretch);
        return context.getString(R.string.chat_background_scale_fill);
    }

    public static class Holder extends SimpleSetting.Holder<ChatBackgroundSetting> {

        public Holder(View itemView, SettingsListAdapter adapter) {
            super(itemView, adapter);
        }

        @Override
        public void onClick(View v) {
            getEntry().showMenu(v, mAdapter);
        }
    }
}
