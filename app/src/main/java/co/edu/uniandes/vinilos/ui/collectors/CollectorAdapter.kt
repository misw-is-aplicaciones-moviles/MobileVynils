package co.edu.uniandes.vinilos.ui.collectors

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import co.edu.uniandes.vinilos.R
import co.edu.uniandes.vinilos.databinding.ItemCollectorBinding
import co.edu.uniandes.vinilos.viewmodels.CollectorItem
import co.edu.uniandes.vinilos.viewmodels.CollectorSummaryLine

/** Collector rows. A tap reports the id; the adapter never touches data or navigation. */
class CollectorAdapter(
    private val onCollectorClick: (Int) -> Unit,
) : ListAdapter<CollectorItem, CollectorViewHolder>(CollectorItemDiff) {
    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): CollectorViewHolder {
        val binding = ItemCollectorBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CollectorViewHolder(binding, onCollectorClick)
    }

    override fun onBindViewHolder(
        holder: CollectorViewHolder,
        position: Int,
    ) {
        holder.bind(getItem(position))
    }
}

class CollectorViewHolder(
    private val binding: ItemCollectorBinding,
    private val onCollectorClick: (Int) -> Unit,
) : RecyclerView.ViewHolder(binding.root) {
    fun bind(item: CollectorItem) {
        val context = binding.root.context
        val summary = summaryOf(context, item.summary)
        binding.collectorNumber.text = item.position
        binding.collectorInitials.text = item.initials
        binding.collectorName.text = item.name
        binding.collectorEmail.text = item.email
        binding.collectorSummary.text = summary
        // The row is a single accessibility node: its children are hidden from TalkBack.
        binding.root.contentDescription =
            context.getString(R.string.collector_item_description, item.name, item.email, summary)
        binding.root.setOnClickListener { onCollectorClick(item.id) }
    }

    private fun summaryOf(
        context: Context,
        summary: CollectorSummaryLine,
    ): String =
        when (summary) {
            is CollectorSummaryLine.Counts -> {
                val resources = context.resources
                context.getString(
                    R.string.collector_summary,
                    resources.getQuantityString(R.plurals.collector_albums_count, summary.albums, summary.albums),
                    resources.getQuantityString(
                        R.plurals.collector_favorites_count,
                        summary.favorites,
                        summary.favorites,
                    ),
                )
            }

            CollectorSummaryLine.NoAlbums -> {
                context.getString(R.string.collector_no_albums)
            }
        }
}

private object CollectorItemDiff : DiffUtil.ItemCallback<CollectorItem>() {
    override fun areItemsTheSame(
        oldItem: CollectorItem,
        newItem: CollectorItem,
    ): Boolean = oldItem.id == newItem.id

    override fun areContentsTheSame(
        oldItem: CollectorItem,
        newItem: CollectorItem,
    ): Boolean = oldItem == newItem
}
