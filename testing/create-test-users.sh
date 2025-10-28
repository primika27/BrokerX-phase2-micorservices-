#!/bin/bash

# Script pour créer des utilisateurs de test et récupérer leurs tokens
BASE_URL="http://localhost:80"

echo "🔧 Création d'utilisateurs de test pour load testing..."

# Créer plusieurs utilisateurs de test
users=(
    "testuser1:Test123!"
    "testuser2:Test123!"
    "testuser3:Test123!" 
    "testuser4:Test123!"
    "testuser5:Test123!"
)

tokens_file="test-users-tokens.json"
echo "[" > $tokens_file

for i in "${!users[@]}"; do
    IFS=':' read -r username password <<< "${users[$i]}"
    
    echo "Créating user: $username"
    
    # 1. Inscription
    register_response=$(curl -s -X POST "$BASE_URL/api/auth/register" \
        -H "Content-Type: application/json" \
        -d "{
            \"username\": \"$username\",
            \"email\": \"${username}@test.com\",
            \"password\": \"$password\",
            \"firstName\": \"Test\",
            \"lastName\": \"User$((i+1))\"
        }")
    
    echo "Register response: $register_response"
    
    # 2. Login pour récupérer le token
    login_response=$(curl -s -X POST "$BASE_URL/api/auth/login" \
        -H "Content-Type: application/json" \
        -d "{
            \"username\": \"$username\",
            \"password\": \"$password\"
        }")
    
    echo "Login response: $login_response"
    
    # Extraire le token (si le format JSON est correct)
    token=$(echo "$login_response" | jq -r '.token // .accessToken // .jwt // empty')
    
    if [ "$token" != "" ] && [ "$token" != "null" ]; then
        echo "✅ Token récupéré pour $username"
        
        # Ajouter au fichier JSON
        if [ $i -gt 0 ]; then
            echo "," >> $tokens_file
        fi
        echo "  {" >> $tokens_file
        echo "    \"username\": \"$username\"," >> $tokens_file
        echo "    \"token\": \"$token\"," >> $tokens_file
        echo "    \"userId\": $((i+1))" >> $tokens_file
        echo "  }" >> $tokens_file
    else
        echo "❌ Échec récupération token pour $username"
    fi
    
    sleep 1
done

echo "]" >> $tokens_file

echo "✅ Tokens sauvegardés dans $tokens_file"
cat $tokens_file