package co.edu.uniandes.vinilos.ui.artists

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import co.edu.uniandes.vinilos.R
import co.edu.uniandes.vinilos.databinding.ItemPrizeBinding
import co.edu.uniandes.vinilos.models.Prize

internal data class PrizeRow(
    val prize: Prize,
    val assigned: Boolean,
    val selected: Boolean,
)

internal class PrizeAdapter(
    private val onSelect: (Int) -> Unit,
) : ListAdapter<PrizeRow, PrizeAdapter.Holder>(Rows) {
    init {
        stateRestorationPolicy = StateRestorationPolicy.PREVENT_WHEN_EMPTY
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): Holder = Holder(ItemPrizeBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(
        holder: Holder,
        position: Int,
    ) = holder.bind(getItem(position))

    inner class Holder(
        private val binding: ItemPrizeBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(row: PrizeRow) {
            binding.prizeName.text = row.prize.name
            binding.prizeOrganization.text = row.prize.organization
            binding.alreadyAssigned.isVisible = row.assigned
            binding.prizeRadio.isVisible = !row.assigned
            binding.prizeRadio.isChecked = row.selected
            binding.root.isSelected = row.selected
            binding.root.isEnabled = !row.assigned
            binding.root.contentDescription =
                binding.root.context.getString(
                    if (row.assigned) R.string.prize_row_assigned_description else R.string.prize_row_description,
                    row.prize.name,
                    row.prize.organization,
                )
            binding.root.setOnClickListener { onSelect(row.prize.id) }
        }
    }

    private object Rows : DiffUtil.ItemCallback<PrizeRow>() {
        override fun areItemsTheSame(
            oldItem: PrizeRow,
            newItem: PrizeRow,
        ) = oldItem.prize.id == newItem.prize.id

        override fun areContentsTheSame(
            oldItem: PrizeRow,
            newItem: PrizeRow,
        ) = oldItem == newItem
    }
}
