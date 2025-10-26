import React from "react";
import ReactDOM from "react-dom/client";
import { createBrowserRouter, RouterProvider } from "react-router-dom";
import { AuthProvider, Protected } from "../lib/auth";
import Home from "../pages/Home";
import VerifySuccess from "../pages/VerifySuccess";
import VerifyFailed from "../pages/VerifyFailed";
import Login from "../pages/Login";
import Register from "../pages/Register";
import Dashboard from "../pages/Dashboard";
import PlaceOrder from "../pages/PlaceOrder";
import Deposit from "../pages/Deposit";
import App from "./App";


const router = createBrowserRouter([
  {
    path: "/",
    element: <App/>, // top layout (nav + outlet)
    children: [
      { index: true, element: <Home /> },
      { path: "login", element: <Login /> },
      { path: "register", element: <Register /> },
      { path: "verify/success", element: <VerifySuccess /> },
      { path: "verify/failed", element: <VerifyFailed /> },

      // protected area
      { path: "dashboard", element: <Protected><Dashboard /></Protected> },
      { path: "placeOrder", element: <Protected><PlaceOrder /></Protected> },
      { path: "deposit", element: <Protected><Deposit /></Protected> },
    ],
  },
]);

ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <AuthProvider>
      <RouterProvider router={router} />
    </AuthProvider>
  </React.StrictMode>
);
