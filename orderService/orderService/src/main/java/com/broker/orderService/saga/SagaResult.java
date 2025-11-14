package com.broker.orderService.saga;

import java.util.ArrayList;
import java.util.List;

/**
 * Résultat d'une exécution de Saga
 * Contient l'état de succès/échec et les étapes executées
 */
public class SagaResult {
    private boolean success;
    private String message;
    private String errorMessage;
    private List<String> executedSteps = new ArrayList<>();

    public SagaResult() {
        this.success = false;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public List<String> getExecutedSteps() {
        return executedSteps;
    }

    public void addStep(String step) {
        this.executedSteps.add(step);
    }

    @Override
    public String toString() {
        return "SagaResult{" +
                "success=" + success +
                ", message='" + message + '\'' +
                ", errorMessage='" + errorMessage + '\'' +
                ", executedSteps=" + executedSteps +
                '}';
    }
}
