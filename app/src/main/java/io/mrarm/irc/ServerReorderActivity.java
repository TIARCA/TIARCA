package io.mrarm.irc;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import io.mrarm.irc.config.ServerConfigData;
import io.mrarm.irc.config.ServerConfigManager;

public class ServerReorderActivity extends ThemedActivity {

    public static void start(Context context) {
        Intent intent = new Intent(context, ServerReorderActivity.class);
        context.startActivity(intent);
    }

    private ServerReorderAdapter mAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_server_reorder);
        setTitle(R.string.action_reorder);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        List<ServerConfigData> servers = new ArrayList<>(ServerConfigManager.getInstance(this).getServers());

        RecyclerView recyclerView = findViewById(R.id.items);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.addItemDecoration(new DividerItemDecoration(this, layoutManager.getOrientation()));

        mAdapter = new ServerReorderAdapter(servers);
        mAdapter.attachToRecyclerView(recyclerView);
        recyclerView.setAdapter(mAdapter);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_edit_only_done, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_done) {
            saveAndFinish();
            return true;
        } else if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void saveAndFinish() {
        List<ServerConfigData> currentList = mAdapter.getServers();
        List<UUID> newOrder = new ArrayList<>();
        for (ServerConfigData data : currentList) {
            newOrder.add(data.uuid);
        }
        try {
            ServerConfigManager.getInstance(this).saveServerOrder(newOrder);
        } catch (IOException e) {
            e.printStackTrace();
        }
        finish();
    }

    private static class ServerReorderAdapter extends RecyclerView.Adapter<ServerReorderAdapter.ServerHolder> {

        private final List<ServerConfigData> mServers;
        private ItemTouchHelper mItemTouchHelper;

        public ServerReorderAdapter(List<ServerConfigData> servers) {
            mServers = servers;
        }

        public List<ServerConfigData> getServers() {
            return mServers;
        }

        public void attachToRecyclerView(RecyclerView recyclerView) {
            ItemTouchHelper.Callback callback = new ItemTouchHelper.Callback() {
                @Override
                public boolean isLongPressDragEnabled() {
                    return true;
                }

                @Override
                public boolean isItemViewSwipeEnabled() {
                    return false;
                }

                @Override
                public int getMovementFlags(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                    return makeMovementFlags(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0);
                }

                @Override
                public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                    int fromPosition = viewHolder.getBindingAdapterPosition();
                    int toPosition = target.getBindingAdapterPosition();
                    if (fromPosition == RecyclerView.NO_POSITION || toPosition == RecyclerView.NO_POSITION)
                        return false;
                    if (fromPosition < toPosition) {
                        for (int i = fromPosition; i < toPosition; i++) {
                            Collections.swap(mServers, i, i + 1);
                        }
                    } else {
                        for (int i = fromPosition; i > toPosition; i--) {
                            Collections.swap(mServers, i, i - 1);
                        }
                    }
                    notifyItemMoved(fromPosition, toPosition);
                    return true;
                }

                @Override
                public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                }
            };
            mItemTouchHelper = new ItemTouchHelper(callback);
            mItemTouchHelper.attachToRecyclerView(recyclerView);
        }

        @NonNull
        @Override
        public ServerHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_server_reorder, parent, false);
            return new ServerHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ServerHolder holder, int position) {
            ServerConfigData data = mServers.get(position);
            holder.mName.setText(data.name);
            String details = data.address + (data.port > 0 ? ":" + data.port : "");
            holder.mAddress.setText(details);
            holder.mReorderHandle.setOnTouchListener((v, event) -> {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN && mItemTouchHelper != null) {
                    mItemTouchHelper.startDrag(holder);
                }
                return false;
            });
        }

        @Override
        public int getItemCount() {
            return mServers.size();
        }

        static class ServerHolder extends RecyclerView.ViewHolder {
            final TextView mName;
            final TextView mAddress;
            final View mReorderHandle;

            public ServerHolder(@NonNull View itemView) {
                super(itemView);
                mName = itemView.findViewById(R.id.server_name);
                mAddress = itemView.findViewById(R.id.server_address);
                mReorderHandle = itemView.findViewById(R.id.reorder_handle);
            }
        }
    }
}
