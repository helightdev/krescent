import {defineConfig} from 'vitepress'

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
          {text: 'Checkpointing', link: '/concepts/checkpointing'},
          {text: 'Models', link: '/concepts/models'}
        ]
      },
      {
        text: 'Guide',
        items: [
          { text: 'Installation', link: '/guide/installation' },
        ]
      }
    ],
    socialLinks: [
      { icon: 'github', link: 'https://github.com/helightdev/krescent' }
    ]
  }
})
