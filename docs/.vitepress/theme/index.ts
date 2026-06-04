import DefaultTheme from "vitepress/theme";
import type { Theme } from "vitepress";
import AstronCasesPage from "./AstronCasesPage.vue";
import Layout from "./Layout.vue";
import "./custom.css";

const theme: Theme = {
  extends: DefaultTheme,
  Layout,
  enhanceApp({ app }) {
    app.component("AstronCasesPage", AstronCasesPage);
  }
};

export default theme;
