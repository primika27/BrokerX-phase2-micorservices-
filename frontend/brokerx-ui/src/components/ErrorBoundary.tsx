import React from "react";

interface ErrorBoundaryProps {
  children: React.ReactNode;
}

interface ErrorBoundaryState {
  hasError: boolean;
  error?: Error;
}

class ErrorBoundary extends React.Component<ErrorBoundaryProps, ErrorBoundaryState> {
  constructor(props: ErrorBoundaryProps) {
    super(props);
    this.state = { hasError: false };
  }

  static getDerivedStateFromError(error: Error): ErrorBoundaryState {
    console.error("ErrorBoundary caught an error:", error);
    return { hasError: true, error };
  }

  componentDidCatch(error: Error, errorInfo: React.ErrorInfo) {
    console.error("ErrorBoundary details:", error, errorInfo);
  }

  render() {
    if (this.state.hasError) {
      return (
        <div style={{ padding: "20px", textAlign: "center" }}>
          <h2>Something went wrong</h2>
          <p>An error occurred while rendering this page.</p>
          <details style={{ marginTop: "10px", textAlign: "left" }}>
            <summary>Error Details</summary>
            <pre style={{ 
              background: "#f5f5f5", 
              padding: "10px", 
              margin: "10px 0", 
              overflow: "auto",
              fontSize: "12px"
            }}>
              {this.state.error?.stack || this.state.error?.message}
            </pre>
          </details>
          <button 
            onClick={() => this.setState({ hasError: false })}
            style={{ 
              padding: "8px 16px", 
              margin: "10px", 
              cursor: "pointer" 
            }}
          >
            Try Again
          </button>
          <button 
            onClick={() => window.location.reload()}
            style={{ 
              padding: "8px 16px", 
              margin: "10px", 
              cursor: "pointer" 
            }}
          >
            Reload Page
          </button>
        </div>
      );
    }

    return this.props.children;
  }
}

export default ErrorBoundary;