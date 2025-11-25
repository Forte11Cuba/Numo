package com.electricdreams.shellshock.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.electricdreams.shellshock.R;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Adapter for the list of allowed mints in settings
 */
public class MintsAdapter extends RecyclerView.Adapter<MintsAdapter.MintViewHolder> {
    
    private List<String> mints;
    private final MintRemoveListener removeListener;
    private final LightningMintSelectedListener lightningListener;
    private String preferredLightningMint;
    private Map<String, Long> mintBalances = new HashMap<>();
    private Map<String, Boolean> loadingStates = new HashMap<>();
    
    /**
     * Interface for handling mint removal
     */
    public interface MintRemoveListener {
        void onMintRemoved(String mintUrl);
    }
    
    /**
     * Interface for handling Lightning mint selection
     */
    public interface LightningMintSelectedListener {
        void onLightningMintSelected(String mintUrl);
    }
    
    public MintsAdapter(List<String> mints, MintRemoveListener listener) {
        this(mints, listener, null, null);
    }
    
    public MintsAdapter(List<String> mints, MintRemoveListener removeListener, 
                       @Nullable LightningMintSelectedListener lightningListener,
                       @Nullable String preferredLightningMint) {
        this.mints = mints;
        this.removeListener = removeListener;
        this.lightningListener = lightningListener;
        this.preferredLightningMint = preferredLightningMint;
        // Initialize all mints as loading
        for (String mint : mints) {
            loadingStates.put(mint, true);
        }
    }
    
    /**
     * Set the preferred Lightning mint URL
     */
    public void setPreferredLightningMint(@Nullable String mintUrl) {
        this.preferredLightningMint = mintUrl;
        notifyDataSetChanged();
    }
    
    /**
     * Update the balance for a specific mint
     */
    public void setMintBalance(String mintUrl, long balance) {
        mintBalances.put(mintUrl, balance);
        loadingStates.put(mintUrl, false);
        int index = mints.indexOf(mintUrl);
        if (index >= 0) {
            notifyItemChanged(index);
        }
    }
    
    /**
     * Update all mint balances at once
     */
    public void setAllBalances(Map<String, Long> balances) {
        mintBalances.clear();
        mintBalances.putAll(balances);
        for (String mint : mints) {
            loadingStates.put(mint, false);
        }
        notifyDataSetChanged();
    }
    
    /**
     * Set loading state for a mint
     */
    public void setLoading(String mintUrl, boolean loading) {
        loadingStates.put(mintUrl, loading);
        int index = mints.indexOf(mintUrl);
        if (index >= 0) {
            notifyItemChanged(index);
        }
    }
    
    /**
     * Set all mints to loading state
     */
    public void setAllLoading() {
        for (String mint : mints) {
            loadingStates.put(mint, true);
        }
        notifyDataSetChanged();
    }
    
    @NonNull
    @Override
    public MintViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_mint, parent, false);
        return new MintViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull MintViewHolder holder, int position) {
        String mint = mints.get(position);
        holder.bind(mint);
    }
    
    @Override
    public int getItemCount() {
        return mints.size();
    }
    
    /**
     * Update the list of mints
     */
    public void updateMints(List<String> newMints) {
        this.mints = newMints;
        // Set new mints as loading
        for (String mint : newMints) {
            if (!loadingStates.containsKey(mint)) {
                loadingStates.put(mint, true);
            }
        }
        notifyDataSetChanged();
    }
    
    /**
     * Extract display-friendly host from mint URL
     */
    private String extractHost(String mintUrl) {
        try {
            URI uri = new URI(mintUrl);
            String host = uri.getHost();
            if (host != null) {
                // Remove www. prefix if present
                if (host.startsWith("www.")) {
                    host = host.substring(4);
                }
                // Add path if it has meaningful content (e.g., /Bitcoin)
                String path = uri.getPath();
                if (path != null && !path.isEmpty() && !path.equals("/")) {
                    return host + path;
                }
                return host;
            }
        } catch (Exception e) {
            // Fall through to return original
        }
        return mintUrl;
    }
    
    /**
     * Format balance in satoshis for display
     */
    private String formatBalance(long sats) {
        if (sats == 0) {
            return "₿0";
        } else if (sats >= 1_000_000) {
            // Show as millions with 2 decimal places
            double millions = sats / 1_000_000.0;
            return String.format("₿%.2fM", millions);
        } else if (sats >= 1_000) {
            // Show as thousands with 1 decimal place
            double thousands = sats / 1_000.0;
            return String.format("₿%.1fK", thousands);
        } else {
            return String.format("₿%,d", sats);
        }
    }
    
    /**
     * ViewHolder for mint items
     */
    class MintViewHolder extends RecyclerView.ViewHolder {
        private final TextView mintUrlText;
        private final TextView mintBalanceText;
        private final ProgressBar balanceLoading;
        private final ImageButton removeButton;
        private final RadioButton lightningRadio;
        
        public MintViewHolder(@NonNull View itemView) {
            super(itemView);
            mintUrlText = itemView.findViewById(R.id.mint_url_text);
            mintBalanceText = itemView.findViewById(R.id.mint_balance_text);
            balanceLoading = itemView.findViewById(R.id.balance_loading);
            removeButton = itemView.findViewById(R.id.remove_mint_button);
            lightningRadio = itemView.findViewById(R.id.lightning_mint_radio);
        }
        
        public void bind(String mintUrl) {
            // Display host name only for cleaner look
            mintUrlText.setText(extractHost(mintUrl));
            
            // Display balance or loading state
            Boolean isLoading = loadingStates.get(mintUrl);
            if (isLoading != null && isLoading) {
                mintBalanceText.setText("Loading...");
                balanceLoading.setVisibility(View.VISIBLE);
            } else {
                Long balance = mintBalances.get(mintUrl);
                mintBalanceText.setText(balance != null ? formatBalance(balance) : "₿0");
                balanceLoading.setVisibility(View.GONE);
            }
            
            // Set up Lightning mint radio button
            boolean isPreferred = mintUrl.equals(preferredLightningMint);
            lightningRadio.setChecked(isPreferred);
            
            lightningRadio.setOnClickListener(v -> {
                if (lightningListener != null) {
                    lightningListener.onLightningMintSelected(mintUrl);
                }
            });
            
            // Also allow clicking the whole card to select as Lightning mint
            itemView.setOnClickListener(v -> {
                if (lightningListener != null) {
                    lightningListener.onLightningMintSelected(mintUrl);
                }
            });
            
            // Set up remove button
            removeButton.setOnClickListener(v -> {
                // Show confirmation dialog before removal
                new androidx.appcompat.app.AlertDialog.Builder(itemView.getContext())
                    .setTitle("Remove Mint")
                    .setMessage("Are you sure you want to remove this mint?\n\n" + mintUrl)
                    .setPositiveButton("Remove", (dialog, which) -> {
                        if (removeListener != null) {
                            removeListener.onMintRemoved(mintUrl);
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            });
        }
    }
}
