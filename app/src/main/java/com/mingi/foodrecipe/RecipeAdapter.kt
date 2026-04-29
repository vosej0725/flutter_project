package com.mingi.foodrecipe

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

class RecipeAdapter(
    private val onRecipeClick: (RecipeRecommendation) -> Unit
) : ListAdapter<RecipeRecommendation, RecipeAdapter.ViewHolder>(DiffCallback) {

    fun submitRecipes(list: List<RecipeRecommendation>) {
        submitList(list)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_recipe, parent, false)
        return ViewHolder(view, onRecipeClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), position + 1)
    }

    class ViewHolder(
        view: View,
        private val onRecipeClick: (RecipeRecommendation) -> Unit
    ) : RecyclerView.ViewHolder(view) {
        private val tvRecipeNumber: TextView = view.findViewById(R.id.tvRecipeNumber)
        private val tvRecipeTitle: TextView = view.findViewById(R.id.tvRecipeTitle)
        private val tvRecipeSummary: TextView = view.findViewById(R.id.tvRecipeSummary)
        private val tvRecipeIngredients: TextView = view.findViewById(R.id.tvRecipeIngredients)

        fun bind(recipe: RecipeRecommendation, number: Int) {
            tvRecipeNumber.text = number.toString()
            tvRecipeTitle.text = recipe.title
            tvRecipeSummary.text = recipe.summary.ifBlank { "감지한 재료로 만들 수 있는 추천 요리입니다." }
            tvRecipeIngredients.text = recipe.ingredients.take(5).joinToString(" · ")
            itemView.setOnClickListener { onRecipeClick(recipe) }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<RecipeRecommendation>() {
        override fun areItemsTheSame(
            oldItem: RecipeRecommendation,
            newItem: RecipeRecommendation
        ): Boolean = oldItem.title == newItem.title

        override fun areContentsTheSame(
            oldItem: RecipeRecommendation,
            newItem: RecipeRecommendation
        ): Boolean = oldItem == newItem
    }
}
