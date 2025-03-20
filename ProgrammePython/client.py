#!/usr/bin/env python3
import socket
import threading
import sys
import time


HOST = 'localhost'  # Adresse du serveur
PORT = 5001  # Port du serveur
TAILLE = 3  # Taille de la grille de jeu


# Couleurs pour le terminal (ne fonctionne Desoler (-_-) que sous Linux/Mac)
class Colors:
    RESET = '\033[0m'
    BLUE = '\033[94m'
    GREEN = '\033[92m'
    RED = '\033[91m'
    BOLD = '\033[1m'


class ClientMorpion:
    def __init__(self):
        self.socket = None
        self.mon_tour = False
        self.pseudo = ""
        self.adversaire = ""
        self.symbole = ""
        self.symbole_adversaire = ""
        self.grille = [[' ' for _ in range(TAILLE)] for _ in range(TAILLE)]
        self.partie_en_cours = True

    def connecter(self):
        try:
            self.socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            self.socket.connect((HOST, PORT))
            print(f"{Colors.GREEN}Connexion établie avec le serveur {HOST}:{PORT}{Colors.RESET}")
            return True
        except Exception as e:
            print(f"{Colors.RED}Erreur de connexion: {e}{Colors.RESET}")
            return False

    def demarrer(self):
        if not self.connecter():
            return

        self.pseudo = input("Entrez votre pseudo: ")
        if not self.pseudo:
            self.pseudo = f"Joueur{int(time.time()) % 1000}"  # Pseudo par defaut = )

        self.envoyer(self.pseudo)

        thread_reception = threading.Thread(target=self.recevoir_messages)
        thread_reception.daemon = True
        thread_reception.start()

        try:
            while self.partie_en_cours:
                if self.mon_tour:
                    self.jouer_coup()
                else:
                    time.sleep(0.1)  # Eviter de surcharger le CPU = )
        except KeyboardInterrupt:
            print("\nDéconnexion...")
        finally:
            if self.socket:
                self.socket.close()

    def recevoir_messages(self):
        try:
            while True:
                message = self.socket.recv(1024).decode('utf-8').strip()
                if not message:
                    print(f"{Colors.RED}Connexion au serveur perdue{Colors.RESET}")
                    self.partie_en_cours = False
                    break

                self.traiter_message(message)
        except Exception as e:
            print(f"{Colors.RED}Erreur de réception: {e}{Colors.RESET}")
            self.partie_en_cours = False

    def traiter_message(self, message):
        if message.startswith("INIT:"):
            # Format: INIT:monPseudo:adversairePseudo:monSymbole:symboleAdversaire
            parts = message.split(":")
            self.pseudo = parts[1]
            self.adversaire = parts[2]
            self.symbole = parts[3]
            self.symbole_adversaire = parts[4]
            print(f"\n{Colors.BOLD}Partie démarrée contre {self.adversaire}{Colors.RESET}")
            print(f"Vous jouez avec le symbole {self.symbole}")

        elif message == "JOUER":
            self.mon_tour = True
            self.afficher_grille()
            print(f"{Colors.BOLD}C'est à votre tour de jouer !{Colors.RESET}")

        elif message == "ATTENDRE":
            self.mon_tour = False
            print(f"En attente du coup de {self.adversaire}...")

        elif message == "REJOUER":
            print(f"{Colors.RED}Case occupée ou invalide, veuillez rejouer{Colors.RESET}")
            self.mon_tour = True
            self.jouer_coup()

        elif message.startswith("MISE_A_JOUR:"):
            etat = message.split(":")[1]
            self.mettre_a_jour_grille(etat)
            self.afficher_grille()

        elif message.startswith("GAGNANT:"):
            gagnant = message.split(":")[1]
            self.afficher_grille()
            if gagnant == self.pseudo:
                print(f"\n{Colors.BOLD}{Colors.GREEN}Félicitations ! Vous avez gagné !{Colors.RESET}")
            else:
                print(f"\n{Colors.BOLD}{Colors.RED}{gagnant} a gagné la partie.{Colors.RESET}")
            self.partie_en_cours = False

        elif message == "MATCH_NUL":
            self.afficher_grille()
            print(f"\n{Colors.BOLD}Match nul !{Colors.RESET}")
            self.partie_en_cours = False

        elif message == "ADVERSAIRE_DECONNECTE":
            print(f"\n{Colors.RED}Votre adversaire s'est déconnecté. Partie terminée.{Colors.RESET}")
            self.partie_en_cours = False

        elif message.startswith("Bienvenue"):
            print(message)

        else:
            print(f"Message du serveur: {message}")

    def jouer_coup(self):
        try:
            coord = input(f"{Colors.BOLD}Entrez votre coup (x,y): {Colors.RESET}")
            try:
                # Valider le format de l'entrée
                x, y = map(int, coord.split(","))
                if x < 0 or x >= TAILLE or y < 0 or y >= TAILLE:
                    print(
                        f"{Colors.RED}Coordonnées hors limites. Elles doivent être entre 0 et {TAILLE - 1}{Colors.RESET}")
                    return

                self.envoyer(f"{x},{y}")
                self.mon_tour = False  # On attend la confirmation du serveur

            except ValueError:
                print(f"{Colors.RED}Format invalide. Utilisez x,y (ex: 1,2){Colors.RESET}")
        except EOFError:
            # Ctrl+D pressé
            self.partie_en_cours = False

    def mettre_a_jour_grille(self, etat):
        cells = etat.split(",")
        index = 0

        for i in range(TAILLE):
            for j in range(TAILLE):
                if index < len(cells):
                    if cells[index] == "X":
                        self.grille[i][j] = "X"
                    elif cells[index] == "O":
                        self.grille[i][j] = "O"
                    else:  # "E" pour vide (Empty)
                        self.grille[i][j] = " "
                    index += 1

    def afficher_grille(self):
        print("\n  " + " ".join([str(i) for i in range(TAILLE)]))
        for i in range(TAILLE):
            ligne = str(i) + " "
            for j in range(TAILLE):
                if self.grille[i][j] == "X":
                    ligne += f"{Colors.BLUE}X{Colors.RESET} "
                elif self.grille[i][j] == "O":
                    ligne += f"{Colors.GREEN}O{Colors.RESET} "
                else:
                    ligne += "· "
            print(ligne)
        print()

    def envoyer(self, message):
        try:
            self.socket.sendall((message + "\n").encode('utf-8'))
        except Exception as e:
            print(f"{Colors.RED}Erreur d'envoi: {e}{Colors.RESET}")
            self.partie_en_cours = False


if __name__ == "__main__":
    print(f"{Colors.BOLD}=== Client Morpion Python ==={Colors.RESET}")
    client = ClientMorpion()
    client.demarrer()