package io.mrarm.irc;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.gson.Gson;

import java.io.InputStreamReader;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import io.mrarm.irc.util.SimpleTextWatcher;

/** Offline, release-vetted IRC network catalogue. */
public class NetworkCatalogActivity extends ThemedActivity {

    private NetworkAdapter mAdapter;
    private ActivityResultLauncher<Intent> mEditServerLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mEditServerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), result -> {
                    if (result.getResultCode() == RESULT_OK)
                        finish();
                });
        setContentView(R.layout.activity_network_catalog);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        Catalog catalog;
        try (InputStreamReader reader = new InputStreamReader(
                getAssets().open("network_catalog.json"), "UTF-8")) {
            catalog = new Gson().fromJson(reader, Catalog.class);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to read the offline network catalogue", e);
        }

        RecyclerView list = findViewById(R.id.network_catalog_list);
        list.setLayoutManager(new LinearLayoutManager(this));
        mAdapter = new NetworkAdapter(catalog.networks == null
                ? Collections.emptyList() : catalog.networks);
        list.setAdapter(mAdapter);

        EditText search = findViewById(R.id.network_catalog_search);
        search.addTextChangedListener(new SimpleTextWatcher((Editable text) ->
                mAdapter.setQuery(text.toString())));

        Spinner sort = findViewById(R.id.network_catalog_sort);
        ArrayAdapter<String> sortAdapter = new ArrayAdapter<>(this,
                R.layout.simple_spinner_item, android.R.id.text1,
                new String[]{getString(R.string.network_catalog_sort_users),
                        getString(R.string.network_catalog_sort_name)});
        sortAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sort.setAdapter(sortAdapter);
        sort.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view,
                                       int position, long id) {
                mAdapter.setSortByName(position == 1);
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        });
    }

    private void openManualEditor() {
        mEditServerLauncher.launch(new Intent(this, EditServerActivity.class));
    }

    private void confirmNetwork(Network network) {
        if (network.endpoints == null || network.endpoints.isEmpty())
            return;
        Endpoint endpoint = network.endpoints.get(0);
        String security = getString(endpoint.tls
                ? R.string.network_catalog_tls : R.string.network_catalog_plaintext);
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.network_catalog_confirm_title, network.name))
                .setMessage(getString(R.string.network_catalog_confirm_body,
                        endpoint.host, endpoint.port, security))
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.network_catalog_continue, (dialog, which) -> {
                    Intent intent = new Intent(this, EditServerActivity.class);
                    intent.putExtra(EditServerActivity.ARG_NAME, network.name);
                    intent.putExtra(EditServerActivity.ARG_ADDRESS, endpoint.host);
                    ArrayList<String> addresses = new ArrayList<>();
                    for (Endpoint candidate : network.endpoints) {
                        if (candidate.port == endpoint.port && candidate.tls == endpoint.tls)
                            addresses.add(candidate.host);
                    }
                    intent.putStringArrayListExtra(EditServerActivity.ARG_ADDRESSES, addresses);
                    intent.putExtra(EditServerActivity.ARG_PORT, endpoint.port);
                    intent.putExtra(EditServerActivity.ARG_SSL, endpoint.tls);
                    mEditServerLauncher.launch(intent);
                })
                .show();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private class NetworkAdapter extends RecyclerView.Adapter<NetworkHolder> {
        private final List<Network> mAll;
        private final List<Network> mShown = new ArrayList<>();
        private String mQuery = "";
        private boolean mSortByName;

        NetworkAdapter(List<Network> networks) {
            mAll = new ArrayList<>(networks);
            rebuild();
        }

        void setQuery(String query) {
            mQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
            rebuild();
        }

        void setSortByName(boolean sortByName) {
            mSortByName = sortByName;
            rebuild();
        }

        private void rebuild() {
            mShown.clear();
            for (Network network : mAll) {
                if (network.populationSnapshot != null &&
                        network.populationSnapshot.users != null &&
                        network.populationSnapshot.users < 100)
                    continue;
                if (mQuery.length() == 0 || network.name.toLowerCase(Locale.ROOT).contains(mQuery))
                    mShown.add(network);
            }
            Comparator<Network> comparator;
            if (mSortByName) {
                comparator = (left, right) -> left.name.compareToIgnoreCase(right.name);
            } else {
                comparator = (left, right) -> {
                    long a = left.populationSnapshot == null || left.populationSnapshot.users == null
                            ? -1 : left.populationSnapshot.users;
                    long b = right.populationSnapshot == null || right.populationSnapshot.users == null
                            ? -1 : right.populationSnapshot.users;
                    int result = Long.compare(b, a);
                    return result != 0 ? result : left.name.compareToIgnoreCase(right.name);
                };
            }
            Collections.sort(mShown, comparator);
            notifyDataSetChanged();
        }

        @Override
        public NetworkHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new NetworkHolder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.network_catalog_item, parent, false));
        }

        @Override
        public void onBindViewHolder(NetworkHolder holder, int position) {
            if (position == 0) {
                holder.bindManual();
            } else {
                holder.bindNetwork(mShown.get(position - 1));
            }
        }

        @Override
        public int getItemCount() {
            return mShown.size() + 1;
        }
    }

    private class NetworkHolder extends RecyclerView.ViewHolder {
        private final TextView mName;
        private final TextView mSummary;

        NetworkHolder(View itemView) {
            super(itemView);
            mName = itemView.findViewById(R.id.network_catalog_name);
            mSummary = itemView.findViewById(R.id.network_catalog_summary);
        }

        void bindManual() {
            mName.setText(R.string.network_catalog_manual);
            mSummary.setText(R.string.network_catalog_manual_summary);
            itemView.setOnClickListener(view -> openManualEditor());
        }

        void bindNetwork(Network network) {
            mName.setText(network.name);
            Population population = network.populationSnapshot;
            if (population != null && population.users != null) {
                NumberFormat format = NumberFormat.getIntegerInstance();
                String channels = population.channels == null ? "—" : format.format(population.channels);
                mSummary.setText(getString(R.string.network_catalog_approximately,
                        format.format(population.users), channels));
            } else {
                mSummary.setText(R.string.network_catalog_unavailable);
            }
            itemView.setOnClickListener(view -> confirmNetwork(network));
        }
    }

    private static class Catalog {
        List<Network> networks;
    }

    private static class Network {
        String name;
        List<Endpoint> endpoints;
        Population populationSnapshot;
    }

    private static class Endpoint {
        String host;
        int port;
        boolean tls;
    }

    private static class Population {
        Long users;
        Long channels;
    }
}
