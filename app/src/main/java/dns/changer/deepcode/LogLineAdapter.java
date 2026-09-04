package dns.changer.deepcode;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class LogLineAdapter extends RecyclerView.Adapter<LogLineAdapter.LineHolder> {

    private final ArrayList<String> lines = new ArrayList<>();
    private int cap = 1000;

    public void setCap(int cap) {
        this.cap = Math.max(1, cap);
        boolean trimmed = false;
        while (lines.size() > this.cap) {
            lines.remove(0);
            trimmed = true;
        }
        if (trimmed) notifyDataSetChanged();
    }

    public void setInitialLines(List<String> initial) {
        lines.clear();
        lines.addAll(initial);
        while (lines.size() > cap) {
            lines.remove(0);
        }
        notifyDataSetChanged();
    }

    
    public void appendLine(String line) {
        if (lines.size() >= cap && !lines.isEmpty()) {
            lines.remove(0);
            notifyItemRemoved(0);
        }
        lines.add(line);
        notifyItemInserted(lines.size() - 1);
    }

    public void clearAll() {
        int size = lines.size();
        lines.clear();
        if (size > 0) notifyItemRangeRemoved(0, size);
    }

    public int getLineCount() {
        return lines.size();
    }

    @NonNull
    @Override
    public LineHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_log_line, parent, false);
        return new LineHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull LineHolder holder, int position) {
        holder.bind(position >= 0 && position < lines.size() ? lines.get(position) : "");
    }

    @Override
    public int getItemCount() {
        return lines.size();
    }

    static class LineHolder extends RecyclerView.ViewHolder {
        final TextView textView;

        LineHolder(@NonNull View itemView) {
            super(itemView);
            textView = (TextView) itemView;
        }

        void bind(String line) {
            textView.setText(Utils.colorizeWords(line));
        }
    }
}
