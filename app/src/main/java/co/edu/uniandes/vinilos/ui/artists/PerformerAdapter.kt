package co.edu.uniandes.vinilos.ui.artists

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import co.edu.uniandes.vinilos.R
import co.edu.uniandes.vinilos.databinding.ItemPerformerBinding
import co.edu.uniandes.vinilos.models.PerformerType
import co.edu.uniandes.vinilos.viewmodels.PerformerItem
import co.edu.uniandes.vinilos.viewmodels.PerformerSummaryLine
import com.bumptech.glide.RequestManager

/**
 * Artist rows. A tap reports the id and type; the adapter never touches data or navigation. Images go through the
 * [requestManager] of the Fragment that owns the list, instead of resolving one from each row's view.
 */
class PerformerAdapter(
    private val requestManager: RequestManager,
    private val onPerformerClick: (Int, PerformerType) -> Unit,
) : ListAdapter<PerformerItem, PerformerViewHolder>(PerformerItemDiff) {
    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): PerformerViewHolder {
        val binding = ItemPerformerBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PerformerViewHolder(binding, requestManager, onPerformerClick)
    }

    override fun onBindViewHolder(
        holder: PerformerViewHolder,
        position: Int,
    ) {
        holder.bind(getItem(position))
    }

    override fun onViewRecycled(holder: PerformerViewHolder) = holder.unbind()
}

/**
 * Sets its click listener once, for the artist bound at the time of the tap, and clears its image when recycled, so
 * a row off screen neither keeps the bitmap nor finishes a download nobody will see.
 */
class PerformerViewHolder(
    private val binding: ItemPerformerBinding,
    private val requestManager: RequestManager,
    onPerformerClick: (Int, PerformerType) -> Unit,
) : RecyclerView.ViewHolder(binding.root) {
    private var current: PerformerItem? = null

    init {
        binding.root.setOnClickListener { current?.let { onPerformerClick(it.id, it.type) } }
    }

    fun bind(item: PerformerItem) {
        current = item
        val context = binding.root.context
        val overline = overlineOf(context, item)
        val summary = summaryOf(context, item.summary)
        binding.performerName.text = item.name
        binding.performerOverline.text = overline
        binding.performerSummary.text = summary
        // The row is a single accessibility node: its children are hidden from TalkBack.
        binding.root.contentDescription =
            context.getString(R.string.artist_item_description, item.name, overline, summary)
        requestManager
            .load(item.imageUrl)
            .placeholder(R.drawable.bg_performer_image_placeholder)
            .error(R.drawable.bg_performer_image_placeholder)
            .centerCrop()
            .into(binding.performerImage)
    }

    fun unbind() {
        current = null
        requestManager.clear(binding.performerImage)
    }

    private fun overlineOf(
        context: Context,
        item: PerformerItem,
    ): String =
        when (item.type) {
            PerformerType.BAND -> {
                val since = context.getString(R.string.artist_since, item.year)
                context.getString(R.string.artist_overline, context.getString(R.string.artist_band), since)
            }

            PerformerType.MUSICIAN -> {
                val born = item.year.toString()
                context.getString(R.string.artist_overline, context.getString(R.string.artist_musician), born)
            }
        }

    private fun summaryOf(
        context: Context,
        summary: PerformerSummaryLine,
    ): String =
        when (summary) {
            is PerformerSummaryLine.BandCounts -> {
                context.getString(
                    R.string.artist_band_summary,
                    albums(context, summary.albums),
                    context.resources.getQuantityString(
                        R.plurals.artist_members_count,
                        summary.members,
                        summary.members,
                    ),
                )
            }

            is PerformerSummaryLine.MemberOf -> {
                context.getString(R.string.artist_member_of, summary.bandName)
            }

            is PerformerSummaryLine.AlbumCount -> {
                albums(context, summary.albums)
            }
        }

    private fun albums(
        context: Context,
        count: Int,
    ): String = context.resources.getQuantityString(R.plurals.artist_albums_count, count, count)
}

private object PerformerItemDiff : DiffUtil.ItemCallback<PerformerItem>() {
    override fun areItemsTheSame(
        oldItem: PerformerItem,
        newItem: PerformerItem,
    ): Boolean = oldItem.id == newItem.id

    override fun areContentsTheSame(
        oldItem: PerformerItem,
        newItem: PerformerItem,
    ): Boolean = oldItem == newItem
}
