package io.mrarm.irc;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import io.mrarm.irc.guide.GuideAdapter;
import io.mrarm.irc.guide.GuideCatalog;

/** Offline help for TIARCA and IRC. Does not send any IRC command. */
public class GuideActivity extends ThemedActivity implements GuideAdapter.CommandListener {
    public static final String EXTRA_INSERT_COMMAND = "guide_insert_command";
    public static final String EXTRA_ALLOW_INSERT = "guide_allow_insert";

    private List<GuideCatalog.Entry> entries = new ArrayList<>();
    private GuideAdapter adapter;
    private TextView empty;
    private EditText search;
    private Spinner category;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_guide);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        search = findViewById(R.id.guide_search);
        category = findViewById(R.id.guide_category);
        empty = findViewById(R.id.guide_empty);
        RecyclerView list = findViewById(R.id.guide_list);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setItemAnimator(null);

        adapter = new GuideAdapter(this, this,
                getIntent().getBooleanExtra(EXTRA_ALLOW_INSERT, false));
        list.setAdapter(adapter);
        try {
            entries = GuideCatalog.parse(loadCatalog());
        } catch (IOException | org.json.JSONException error) {
            empty.setText(R.string.guide_load_error);
            empty.setVisibility(View.VISIBLE);
        }

        ArrayAdapter<CharSequence> categories = ArrayAdapter.createFromResource(this,
                R.array.guide_categories, android.R.layout.simple_spinner_item);
        categories.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        category.setAdapter(categories);
        category.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view,
                                                  int position, long id) { refresh(); }
            @Override public void onNothingSelected(AdapterView<?> parent) { refresh(); }
        });
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                refresh();
            }
            @Override public void afterTextChanged(Editable s) { }
        });
        refresh();
    }

    private String loadCatalog() throws IOException {
        boolean italian = "it".equals(getResources().getConfiguration()
                .getLocales().get(0).getLanguage());
        String filename = italian ? "guide_it.json" : "guide_en.json";
        try (InputStream stream = getAssets().open(filename);
             ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int size;
            while ((size = stream.read(buffer)) != -1)
                bytes.write(buffer, 0, size);
            return bytes.toString(StandardCharsets.UTF_8.name());
        }
    }

    private void refresh() {
        if (adapter == null)
            return;
        int index = category.getSelectedItemPosition();
        if (index < 0 || index >= GuideCatalog.CATEGORIES.length)
            index = 0;
        List<GuideCatalog.Entry> visible = GuideCatalog.filter(entries,
                GuideCatalog.CATEGORIES[index], search.getText().toString());
        adapter.show(visible);
        empty.setVisibility(visible.isEmpty() ? View.VISIBLE : View.GONE);
        if (entries.isEmpty())
            empty.setText(R.string.guide_load_error);
    }

    @Override public void onCopy(String syntax) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("IRC command", syntax));
            Toast.makeText(this, R.string.guide_copied, Toast.LENGTH_SHORT).show();
        }
    }

    @Override public void onInsert(String syntax) {
        if (syntax == null || !syntax.startsWith("/") || syntax.contains("\n")
                || syntax.contains("\r"))
            return;
        Intent result = new Intent().putExtra(EXTRA_INSERT_COMMAND, syntax);
        setResult(Activity.RESULT_OK, result);
        finish();
    }

    @Override public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
