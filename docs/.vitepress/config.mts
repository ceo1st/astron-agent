import { defineConfig } from "vitepress";

const base = process.env.DOCS_BASE || "/";

const socialLinks = [
  { icon: "github", link: "https://github.com/iflytek/astron-agent" }
];

export default defineConfig({
  title: "Astron Agent",
  description: "企业级、商业友好的 Agentic Workflow 开发平台文档站。",
  base,
  cleanUrls: true,
  ignoreDeadLinks: true,
  locales: {
    root: {
      label: "简体中文",
      lang: "zh-CN",
      title: "Astron Agent",
      description: "企业级、商业友好的 Agentic Workflow 开发平台文档站。",
      themeConfig: {
        logo: "/logo-square.png",
        siteTitle: "Astron Agent",
        nav: [
          { text: "首页", link: "/" },
          { text: "快速开始", link: "/guide/quick-start" },
          { text: "部署与运维", link: "/guide/deploy" },
          { text: "案例实践", link: "/cases/" },
          { text: "架构与开发", link: "/PROJECT_MODULES_zh" },
          { text: "贡献协作", link: "/CONTRIBUTING_CN" }
        ],
        sidebar: [
          {
            text: "开始使用",
            items: [
              { text: "项目概览", link: "/README-zh" },
              { text: "快速开始", link: "/guide/quick-start" },
              { text: "FAQ", link: "/faq" }
            ]
          },
          {
            text: "案例实践",
            items: [
              { text: "用户案例", link: "/cases/" }
            ]
          },
          {
            text: "部署与配置",
            items: [
              { text: "部署总览", link: "/guide/deploy" },
              { text: "配置说明", link: "/guide/config" },
              { text: "标准部署指南", link: "/DEPLOYMENT_GUIDE_zh" },
              { text: "鉴权部署指南", link: "/DEPLOYMENT_GUIDE_WITH_AUTH_zh" },
              { text: "鉴权 + RPA 部署指南", link: "/DEPLOYMENT_GUIDE_WITH_AUTH_RPA_zh" },
              { text: "部署常见问题", link: "/DEPLOYMENT_FAQ_zh" },
              { text: "配置参考", link: "/CONFIGURATION_zh" }
            ]
          },
          {
            text: "架构与开发",
            items: [
              { text: "模块说明", link: "/PROJECT_MODULES_zh" },
              { text: "Makefile 使用指南", link: "/Makefile-readme-zh" }
            ]
          },
          {
            text: "贡献协作",
            items: [
              { text: "贡献指南", link: "/CONTRIBUTING_CN" },
              { text: "Pre-commit 使用指南", link: "/PRE-COMMIT_zh" }
            ]
          }
        ],
        socialLinks,
        search: {
          provider: "local"
        },
        langMenuLabel: "语言",
        returnToTopLabel: "返回顶部",
        sidebarMenuLabel: "菜单",
        darkModeSwitchLabel: "主题",
        outline: {
          label: "页面导航"
        },
        docFooter: {
          prev: "上一页",
          next: "下一页"
        },
        footer: {
          message: "Apache 2.0 Licensed.",
          copyright: "Copyright © iFLYTEK Astron Agent"
        }
      }
    },
    en: {
      label: "English",
      lang: "en-US",
      link: "/en/",
      title: "Astron Agent",
      description: "Documentation for the enterprise-grade, commercially-friendly Agentic Workflow platform.",
      themeConfig: {
        logo: "/logo-square.png",
        siteTitle: "Astron Agent",
        nav: [
          { text: "Home", link: "/en/" },
          { text: "Quick Start", link: "/en/guide/quick-start" },
          { text: "Deployment", link: "/en/guide/deploy" },
          { text: "Case Studies", link: "/en/cases/" },
          { text: "Architecture", link: "/en/PROJECT_MODULES" },
          { text: "Contributing", link: "/en/CONTRIBUTING" }
        ],
        sidebar: [
          {
            text: "Getting Started",
            items: [
              { text: "Project Overview", link: "/en/README" },
              { text: "Quick Start", link: "/en/guide/quick-start" },
              { text: "FAQ", link: "/en/faq" }
            ]
          },
          {
            text: "Case Studies",
            items: [
              { text: "Customer Stories", link: "/en/cases/" }
            ]
          },
          {
            text: "Deployment And Configuration",
            items: [
              { text: "Deployment Overview", link: "/en/guide/deploy" },
              { text: "Configuration Guide", link: "/en/guide/config" },
              { text: "Full Deployment Guide", link: "/en/DEPLOYMENT_GUIDE" },
              { text: "Auth Deployment Guide", link: "/en/DEPLOYMENT_GUIDE_WITH_AUTH" },
              { text: "Auth + RPA Deployment Guide", link: "/en/DEPLOYMENT_GUIDE_WITH_AUTH_RPA" },
              { text: "Deployment FAQ", link: "/en/DEPLOYMENT_FAQ" },
              { text: "Configuration Reference", link: "/en/CONFIGURATION" }
            ]
          },
          {
            text: "Architecture And Development",
            items: [
              { text: "Project Modules", link: "/en/PROJECT_MODULES" },
              { text: "Makefile Guide", link: "/en/Makefile-readme" }
            ]
          },
          {
            text: "Contribution",
            items: [
              { text: "Contributing Guide", link: "/en/CONTRIBUTING" },
              { text: "Pre-commit Guide", link: "/en/PRE-COMMIT" }
            ]
          }
        ],
        socialLinks,
        search: {
          provider: "local"
        },
        langMenuLabel: "Languages",
        returnToTopLabel: "Back to top",
        sidebarMenuLabel: "Menu",
        darkModeSwitchLabel: "Theme",
        outline: {
          label: "On this page"
        },
        docFooter: {
          prev: "Previous page",
          next: "Next page"
        },
        footer: {
          message: "Apache 2.0 Licensed.",
          copyright: "Copyright © iFLYTEK Astron Agent"
        }
      }
    }
  }
});
