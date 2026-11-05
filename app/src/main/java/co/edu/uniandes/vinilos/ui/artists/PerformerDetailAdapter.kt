package co.edu.uniandes.vinilos.ui.artists

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.annotation.StringRes
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.core.widget.TextViewCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import co.edu.uniandes.vinilos.R
import co.edu.uniandes.vinilos.databinding.ItemAlbumDetailMessageBinding
import co.edu.uniandes.vinilos.databinding.ItemPerformerAlbumBinding
import co.edu.uniandes.vinilos.databinding.ItemPerformerDetailHeaderBinding
import co.edu.uniandes.vinilos.databinding.ItemPerformerMemberBinding
import co.edu.uniandes.vinilos.databinding.ItemPerformerPrizeActionBinding
import co.edu.uniandes.vinilos.databinding.ItemPrizeBinding
import co.edu.uniandes.vinilos.models.Album
import co.edu.uniandes.vinilos.models.ArtistPrize
import co.edu.uniandes.vinilos.models.PerformerDetail
import co.edu.uniandes.vinilos.models.PerformerSummary
import co.edu.uniandes.vinilos.models.PerformerType
import co.edu.uniandes.vinilos.viewmodels.PerformerDetailUiState
import com.bumptech.glide.Glide
import java.time.format.DateTimeFormatter
import java.util.Locale

internal sealed interface PerformerDetailRow {
    data class PrizeAction(
        val count: Int?,
        val canEdit: Boolean,
        val canRetry: Boolean,
        val loading: Boolean,
    ) : PerformerDetailRow

    data class Award(
        val assignment: ArtistPrize,
        val highlighted: Boolean,
    ) : PerformerDetailRow

    data class Header(
        val detail: PerformerDetail,
    ) : PerformerDetailRow

    data class Text(
        @param:StringRes val label: Int,
        val count: Int? = null,
    ) : PerformerDetailRow

    data class Record(
        val album: Album,
    ) : PerformerDetailRow

    data class Members(
        val members: List<PerformerSummary>,
    ) : PerformerDetailRow
}

/** One vertical surface; the band's members form a horizontal strip inside it. */
internal class PerformerDetailAdapter(
    private val onAlbumClick: (Int) -> Unit,
    private val onMemberClick: (Int) -> Unit,
    private val onAddPrize: () -> Unit,
    private val onRetryPrizes: () -> Unit,
) : ListAdapter<PerformerDetailRow, RecyclerView.ViewHolder>(Rows) {
    init {
        stateRestorationPolicy = StateRestorationPolicy.PREVENT_WHEN_EMPTY
    }

    fun submitDetail(state: PerformerDetailUiState) {
        val detail = state.detail ?: return
        submitList(
            buildList {
                add(PerformerDetailRow.Header(detail))
                if (detail.performer.type == PerformerType.BAND) {
                    add(PerformerDetailRow.Text(R.string.artist_detail_members, detail.members.size))
                    if (detail.members.isEmpty()) {
                        add(PerformerDetailRow.Text(R.string.artist_detail_no_members))
                    } else {
                        add(PerformerDetailRow.Members(detail.members))
                    }
                }
                add(PerformerDetailRow.Text(R.string.artist_detail_albums, detail.albums.size))
                detail.albums.forEach { add(PerformerDetailRow.Record(it)) }
                if (detail.albums.isEmpty()) add(PerformerDetailRow.Text(R.string.artist_detail_no_albums))
                add(
                    PerformerDetailRow.PrizeAction(
                        count = if (state.isLoadingPrizes || state.prizeError != null) null else state.prizes.size,
                        canEdit = state.role == co.edu.uniandes.vinilos.models.UserRole.COLLECTOR,
                        canRetry = state.prizeError != null,
                        loading = state.isLoadingPrizes,
                    ),
                )
                when {
                    state.isLoadingPrizes -> {
                        add(PerformerDetailRow.Text(R.string.prize_detail_loading))
                    }

                    state.prizeError != null -> {
                        add(PerformerDetailRow.Text(R.string.prize_catalog_error))
                    }

                    state.prizes.isEmpty() -> {
                        add(PerformerDetailRow.Text(R.string.prize_detail_empty))
                    }

                    else -> {
                        state.prizes.forEach {
                            add(PerformerDetailRow.Award(it, it.id == state.highlightedAssignmentId))
                        }
                    }
                }
            },
        )
    }

    override fun getItemViewType(position: Int): Int =
        when (getItem(position)) {
            is PerformerDetailRow.PrizeAction -> PRIZE_ACTION
            is PerformerDetailRow.Award -> AWARD
            is PerformerDetailRow.Header -> HEADER
            is PerformerDetailRow.Text -> TEXT
            is PerformerDetailRow.Record -> ALBUM
            is PerformerDetailRow.Members -> MEMBERS
        }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            PRIZE_ACTION -> {
                PrizeActionHolder(ItemPerformerPrizeActionBinding.inflate(inflater, parent, false))
            }

            AWARD -> {
                AwardHolder(ItemPrizeBinding.inflate(inflater, parent, false))
            }

            HEADER -> {
                HeaderHolder(ItemPerformerDetailHeaderBinding.inflate(inflater, parent, false))
            }

            TEXT -> {
                TextHolder(ItemAlbumDetailMessageBinding.inflate(inflater, parent, false))
            }

            ALBUM -> {
                AlbumHolder(ItemPerformerAlbumBinding.inflate(inflater, parent, false), onAlbumClick)
            }

            MEMBERS -> {
                MembersHolder(
                    RecyclerView(parent.context).apply {
                        id = R.id.performerMembers
                        layoutParams =
                            RecyclerView.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                            )
                        layoutManager = LinearLayoutManager(context, RecyclerView.HORIZONTAL, false)
                        clipChildren = false
                        clipToPadding = false
                    },
                    onMemberClick,
                )
            }

            else -> {
                error("Unknown artist detail row")
            }
        }
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
    ) {
        when (val row = getItem(position)) {
            is PerformerDetailRow.PrizeAction -> (holder as PrizeActionHolder).bind(row)
            is PerformerDetailRow.Award -> (holder as AwardHolder).bind(row)
            is PerformerDetailRow.Header -> (holder as HeaderHolder).bind(row.detail)
            is PerformerDetailRow.Text -> (holder as TextHolder).bind(row)
            is PerformerDetailRow.Record -> (holder as AlbumHolder).bind(row.album)
            is PerformerDetailRow.Members -> (holder as MembersHolder).bind(row.members)
        }
    }

    private inner class PrizeActionHolder(
        private val binding: ItemPerformerPrizeActionBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(row: PerformerDetailRow.PrizeAction) {
            ViewCompat.setAccessibilityHeading(binding.prizeLabel, true)
            binding.prizeLabel.text =
                if (row.count == null) {
                    binding.root.context.getString(R.string.artist_detail_prizes)
                } else {
                    binding.root.context.getString(R.string.prize_detail_count, row.count)
                }
            binding.addPrizeButton.isVisible = row.canEdit || row.canRetry
            binding.addPrizeButton.isEnabled = !row.loading
            binding.addPrizeButton.setText(if (row.canRetry) R.string.action_retry else R.string.artist_add_prize)
            binding.addPrizeButton.setIconResource(if (row.canRetry) 0 else R.drawable.ic_add)
            binding.addPrizeButton.setOnClickListener { if (row.canRetry) onRetryPrizes() else onAddPrize() }
        }
    }

    private class AwardHolder(
        private val binding: ItemPrizeBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(row: PerformerDetailRow.Award) {
            val assignment = row.assignment
            val date = assignment.premiationDate.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()))
            binding.prizeName.text = assignment.prize.name
            binding.prizeName.isAllCaps = false
            binding.prizeOrganization.text = assignment.prize.organization
            binding.alreadyAssigned.isVisible = true
            binding.alreadyAssigned.text = date
            binding.prizeRadio.isVisible = false
            binding.root.isClickable = false
            binding.root.isSelected = row.highlighted
            binding.root.contentDescription =
                binding.root.context.getString(
                    R.string.prize_detail_description,
                    assignment.prize.name,
                    assignment.prize.organization,
                    date,
                )
        }
    }

    private class HeaderHolder(
        private val binding: ItemPerformerDetailHeaderBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(detail: PerformerDetail) {
            val artist = detail.performer
            val context = binding.root.context
            binding.performerName.text = artist.name
            ViewCompat.setAccessibilityHeading(binding.performerName, true)
            binding.performerDate.text =
                if (artist.type == PerformerType.MUSICIAN) {
                    context.getString(
                        R.string.artist_detail_birth,
                        artist.date.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())),
                    )
                } else {
                    context.getString(
                        R.string.artist_overline,
                        context.getString(R.string.artist_band),
                        context.getString(R.string.artist_since, artist.date.year),
                    )
                }
            binding.performerDescription.text = detail.description
            binding.artistArtwork.performerImage.loadPerformerImage(artist.imageUrl)
        }
    }

    private class TextHolder(
        private val binding: ItemAlbumDetailMessageBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(row: PerformerDetailRow.Text) {
            val context = binding.root.context
            binding.detailMessage.text =
                if (row.count == null) context.getString(row.label) else context.getString(row.label, row.count)
            TextViewCompat.setTextAppearance(
                binding.detailMessage,
                if (row.count == null) {
                    R.style.TextAppearance_Vinilos_Body
                } else {
                    R.style.TextAppearance_Vinilos_AlbumTitle
                },
            )
            ViewCompat.setAccessibilityHeading(binding.detailMessage, row.count != null)
        }
    }

    private class AlbumHolder(
        private val binding: ItemPerformerAlbumBinding,
        private val onClick: (Int) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(album: Album) {
            binding.albumTitle.text = album.name
            binding.albumYear.text = album.releaseDate.year.toString()
            binding.root.contentDescription =
                binding.root.context.getString(
                    R.string.artist_detail_album_description,
                    album.name,
                    album.releaseDate.year,
                )
            binding.root.setOnClickListener { onClick(album.id) }
            Glide
                .with(binding.albumArtwork.coverView)
                .load(album.coverUrl)
                .placeholder(R.drawable.ic_nav_albums)
                .error(R.drawable.ic_nav_albums)
                .centerCrop()
                .into(binding.albumArtwork.coverView)
        }
    }

    private class MembersHolder(
        private val list: RecyclerView,
        private val onClick: (Int) -> Unit,
    ) : RecyclerView.ViewHolder(list) {
        fun bind(members: List<PerformerSummary>) {
            list.adapter = MemberAdapter(members, onClick)
        }
    }

    private class MemberAdapter(
        private val members: List<PerformerSummary>,
        private val onClick: (Int) -> Unit,
    ) : RecyclerView.Adapter<MemberHolder>() {
        override fun getItemCount(): Int = members.size

        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int,
        ) = MemberHolder(
            ItemPerformerMemberBinding.inflate(LayoutInflater.from(parent.context), parent, false),
            onClick,
        )

        override fun onBindViewHolder(
            holder: MemberHolder,
            position: Int,
        ) = holder.bind(members[position])
    }

    private class MemberHolder(
        private val binding: ItemPerformerMemberBinding,
        private val onClick: (Int) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(member: PerformerSummary) {
            binding.memberName.text = member.name
            binding.memberArtwork.performerImage.loadPerformerImage(member.imageUrl)
            binding.root.contentDescription = binding.root.context.getString(R.string.artist_open_detail, member.name)
            binding.root.setOnClickListener { onClick(member.id) }
        }
    }

    private object Rows : DiffUtil.ItemCallback<PerformerDetailRow>() {
        private fun key(row: PerformerDetailRow): String =
            when (row) {
                is PerformerDetailRow.PrizeAction -> "prize-action"
                is PerformerDetailRow.Award -> "award-${row.assignment.id}"
                is PerformerDetailRow.Header -> "header"
                is PerformerDetailRow.Text -> "text-${row.label}"
                is PerformerDetailRow.Record -> "album-${row.album.id}"
                is PerformerDetailRow.Members -> "members"
            }

        override fun areItemsTheSame(
            oldItem: PerformerDetailRow,
            newItem: PerformerDetailRow,
        ): Boolean = key(oldItem) == key(newItem)

        override fun areContentsTheSame(
            oldItem: PerformerDetailRow,
            newItem: PerformerDetailRow,
        ) = oldItem == newItem
    }

    private companion object {
        const val HEADER = 0
        const val TEXT = 1
        const val ALBUM = 2
        const val MEMBERS = 3
        const val PRIZE_ACTION = 4
        const val AWARD = 5
    }
}

private fun ImageView.loadPerformerImage(url: String?) {
    Glide
        .with(this)
        .load(url)
        .placeholder(R.drawable.bg_performer_image_placeholder)
        .error(R.drawable.bg_performer_image_placeholder)
        .centerCrop()
        .into(this)
}
