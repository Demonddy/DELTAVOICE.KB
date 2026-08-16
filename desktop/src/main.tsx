import React from "react";
import ReactDOM from "react-dom/client";
import App from "./App";
import { getPlatformInfo } from "./utils/platform";
import "./styles/globals.css";

getPlatformInfo();

ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>
);
