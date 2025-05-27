import { defineConfig } from 'vitepress'

// https://vitepress.dev/reference/site-config
export default defineConfig({
  title: "Krescent",
  description: "Kotlin Event Sourcing Library",
  base: '/krescent/',
  cleanUrls: true,
  lang: 'en-US',
  appearance: 'dark',
  lastUpdated: true,
  ignoreDeadLinks: 'localhostLinks',
  themeConfig: {
    // https://vitepress.dev/reference/default-theme-config
    nav: [
      { text: 'Home', link: '/' },
      { text: 'Guide', link: '/guide/installation' }
    ],
    search: {
      provider: "local"
    },

    footer: {
      copyright: "Released under the Apache License 2.0",
    },

    sidebar: [
      {
        text: 'Concepts',
        items: [
          { text: 'Events', link: '/concepts/events' },
          { text: 'Event Catalog', link: '/concepts/event-catalog' },
          { text: 'Event Stream Processing', link: '/concepts/event-stream-processing' },
          { text: 'Event Sources', link: '/concepts/event-sources' },
          { text: 'Event Sourcing Strategies', link: '/concepts/event-sourcing-strategies' },
          { text: 'Event Models', link: '/concepts/event-models' },
          { text: 'Checkpointing', link: '/concepts/checkpointing' }
        ]
      },
      {
        text: 'Guide',
        items: [
          { text: 'Installation', link: '/guide/installation' },
          { text: 'Getting Started', link: '/guide/getting-started' },
          { text: 'Creating Event Models', link: '/guide/creating-event-models' },
          { text: 'Working with Event Sources', link: '/guide/working-with-event-sources' }
        ]
      }
    ],

    socialLinks: [
      { icon: 'github', link: 'https://github.com/helightdev/krescent' }
    ]
  }
})
