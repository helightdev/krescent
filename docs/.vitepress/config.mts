import {defineConfig} from 'vitepress'

// https://vitepress.dev/reference/site-config
export default defineConfig({
    title: "Krescent",
    description: "Kotlin Event Sourcing Library",
    base: '/',
    cleanUrls: true,
    lang: 'en-US',
    appearance: 'dark',
    lastUpdated: true,
    ignoreDeadLinks: 'localhostLinks',
    themeConfig: {
        // https://vitepress.dev/reference/default-theme-config
        nav: [
            {text: 'Home', link: '/'},
            {text: 'Guide', link: '/guide/installation'}
        ],
        search: {
            provider: "local",
            options: {
                detailedView: true,
            }
        },

        footer: {
            copyright: "Released under the Apache License 2.0",
        },

        sidebar: [
            {
                text: 'Guide',
                items: [
                    {text: 'Installation', link: '/guide/installation'},
                    {text: 'Events', link: '/guide/events'},
                    {text: 'Models', link: '/guide/models'},
                    {text: 'Checkpointing', link: '/guide/checkpointing'}
                ]
            }
        ],
        socialLinks: [
            {icon: 'github', link: 'https://github.com/helightdev/krescent'}
        ]
    }
})
