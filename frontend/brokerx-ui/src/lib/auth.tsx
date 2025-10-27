import React, { useEffect, useState } from "react";
import { Navigate, useLocation } from "react-router-dom";
import { AuthContext } from "./auth-context";
import { useAuth } from "./useAuth";



// JWT validation helper function
function isJwtValid(jwt: string | null): boolean {
  if (!jwt) {
    console.log("JWT validation: no token provided");
    return false;
  }
  
  try {
    const parts = jwt.split('.');
    if (parts.length !== 3) {
      console.log("JWT validation: invalid token format");
      return false;
    }
    
    // Parse JWT payload (simple base64 decode of middle section)
    const payload = JSON.parse(atob(parts[1]));
    const currentTime = Date.now() / 1000; // Current time in seconds
    
    console.log("JWT validation:", {
      exp: payload.exp,
      currentTime,
      isValid: payload.exp > currentTime
    });
    
    // Check if token is expired
    return payload.exp > currentTime;
  } catch (error) {
    // If parsing fails, token is invalid
    console.log("JWT validation error:", error);
    return false;
  }
}

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [jwt, setJwt] = useState<string | null>(null);

  // Initialize JWT from localStorage on component mount
  useEffect(() => { 
    const storedJwt = localStorage.getItem("jwt");
    console.log("AuthProvider initializing with stored JWT:", storedJwt ? "present" : "not found");
    
    // Only set JWT if it's valid and not expired
    if (isJwtValid(storedJwt)) {
      console.log("Setting valid JWT from localStorage");
      setJwt(storedJwt);
    } else {
      console.log("Clearing invalid/expired JWT from localStorage");
      // Clear invalid/expired token
      localStorage.removeItem("jwt");
    }
  }, []);

  // Sync JWT changes to localStorage
  useEffect(() => {
    if (jwt) {
      if (isJwtValid(jwt)) {
        localStorage.setItem("jwt", jwt);
      } else {
        // Token is invalid, clear it
        localStorage.removeItem("jwt");
        setJwt(null);
      }
    } else {
      localStorage.removeItem("jwt");
    }
  }, [jwt]);

  const logout = () => {
    setJwt(null);
    localStorage.removeItem("jwt");
  };

  // Periodically check if JWT is still valid (every 30 seconds)
  useEffect(() => {
    if (!jwt) return;

    const interval = setInterval(() => {
      if (jwt && !isJwtValid(jwt)) {
        console.log("JWT expired during session, logging out");
        logout();
      }
    }, 30000); // Check every 30 seconds

    return () => clearInterval(interval);
  }, [jwt]);

  return <AuthContext.Provider value={{ jwt, setJwt, logout }}>{children}</AuthContext.Provider>;
}

export function Protected({ children }: { children: React.ReactNode }) {
  const { jwt, logout } = useAuth();
  const loc = useLocation();
  
  console.log("Protected component - JWT check:", {
    hasJwt: !!jwt,
    jwtValid: isJwtValid(jwt),
    location: loc.pathname
  });
  
  if (!jwt || !isJwtValid(jwt)) {
    console.log("Protected route access denied - redirecting to login");
    if (jwt && !isJwtValid(jwt)) {
      // Token exists but is invalid/expired, clear it
      logout();
    }
    return <Navigate to="/login" replace state={{ from: loc }} />;
  }
  
  return <>{children}</>;
}
