import React, { useEffect, useState } from "react";
import { Navigate, useLocation } from "react-router-dom";
import { AuthContext } from "./auth-context";
import { useAuth } from "./useAuth";



export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [jwt, setJwt] = useState<string | null>(null);

  useEffect(() => { setJwt(localStorage.getItem("jwt")); }, []);
  useEffect(() => {
    if (jwt) localStorage.setItem("jwt", jwt);
    else localStorage.removeItem("jwt");
  }, [jwt]);

  const logout = () => {
    setJwt(null); // clear token from context
    localStorage.removeItem("jwt"); // optional if already handled
  };

  return <AuthContext.Provider value={{ jwt, setJwt,logout }}>{children}</AuthContext.Provider>;
}

export function Protected({ children }: { children: React.ReactNode }) {
  const { jwt } = useAuth();
  const loc = useLocation();
  if (!jwt) return <Navigate to="/login" replace state={{ from: loc }} />;
  return <>{children}</>;
}
