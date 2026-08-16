package io.mrarm.irc.util;

import android.view.View;
import android.view.ViewGroup;

import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class ClickableRecyclerViewAdapter<VH extends ClickableRecyclerViewAdapter.ViewHolder<IT>, IT>
        extends SimpleRecyclerViewAdapter<VH, IT> {

    private ItemClickListener<IT> mItemClickListener;

    public ClickableRecyclerViewAdapter() {
    }

    public ClickableRecyclerViewAdapter(ViewHolderFactory<VH> viewHolderFactory, int viewResId,
                                        List<IT> items) {
        super(viewHolderFactory, viewResId, items);
    }

    public void setItemClickListener(ItemClickListener<IT> listener) {
        mItemClickListener = listener;
    }

    @Override
    public VH onCreateViewHolder(ViewGroup parent, int viewType) {
        VH ret = super.onCreateViewHolder(parent, viewType);
        ret.setClickListener(this);
        return ret;
    }

    public static class ViewHolder<IT> extends SimpleRecyclerViewAdapter.ViewHolder<IT> {

        public ViewHolder(View itemView) {
            super(itemView);
        }

        void setClickListener(ClickableRecyclerViewAdapter<?, IT> adapter) {
            itemView.setOnClickListener((View v) -> {
                int position = getBindingAdapterPosition();
                if (adapter.mItemClickListener != null && position != RecyclerView.NO_POSITION)
                    adapter.mItemClickListener.onItemClick(position, adapter.getItems().get(position));
            });
        }

    }

    public interface ItemClickListener<IT> {

        void onItemClick(int index, IT value);

    }

}
