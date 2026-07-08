package com.svartifoss.snfell.view.mainactivity

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.svartifoss.snfell.R

/**
 * Hosts the usage guide ([TutorialFragment]). The guide used to be a bottom-nav tab; that slot
 * now belongs to the watch face customization screen, so the guide opens from the toolbar's
 * help button instead.
 */
class HelpActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_help)

        setSupportActionBar(findViewById(R.id.help_toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.tutorial_header)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                    .replace(R.id.help_fragment_container, TutorialFragment())
                    .commit()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
