#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <stdbool.h>
#include <signal.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <netdb.h>
#include <fcntl.h>
#include <errno.h>
#include <pthread.h>

#define HOST "localhost"
#define PORT 5001
#define BUFFER_SIZE 1024
#define TAILLE 3

// Definition des couleurs pour le terminal macOS
#define RESET   "\033[0m"
#define BLUE    "\033[34m"
#define GREEN   "\033[32m"
#define RED     "\033[31m"
#define BOLD    "\033[1m"

// Structure representant l'etat du jeu
typedef struct {
    int socket_fd;
    char grille[TAILLE][TAILLE];
    char pseudo[50];
    char adversaire[50];
    char symbole;
    char symbole_adversaire;
    bool mon_tour;
    bool partie_en_cours;
} ClientMorpion;

// Variables globales pour la gestion de la fermeture
ClientMorpion client;
bool running = true;
pthread_t thread_reception;

// Prototypes de fonctions = )
void initialiser_client();
int connecter_au_serveur();
void envoyer_message(const char* message);
int recevoir_message(char* buffer, int taille);
void traiter_message(const char* message);
void afficher_grille();
void mettre_a_jour_grille(const char* etat);
void jouer_coup();
void nettoyer_stdin();
void cleanup();
void handle_signal(int sig);
void* thread_recevoir_messages(void* arg);

// Initialisation du client
void initialiser_client() {
    memset(&client, 0, sizeof(ClientMorpion));

    for (int i = 0; i < TAILLE; i++) {
        for (int j = 0; j < TAILLE; j++) {
            client.grille[i][j] = ' ';
        }
    }

    client.mon_tour = false;
    client.partie_en_cours = true;

    signal(SIGINT, handle_signal);
}

int connecter_au_serveur() {
    struct sockaddr_in server_addr;
    struct hostent *server;

    client.socket_fd = socket(AF_INET, SOCK_STREAM, 0);
    if (client.socket_fd < 0) {
        printf("%sErreur lors de la création du socket%s\n", RED, RESET);
        return -1;
    }

    server = gethostbyname(HOST);
    if (server == NULL) {
        printf("%sErreur: Serveur introuvable%s\n", RED, RESET);
        return -1;
    }

    memset(&server_addr, 0, sizeof(server_addr));
    server_addr.sin_family = AF_INET;
    memcpy(&server_addr.sin_addr.s_addr, server->h_addr, server->h_length);
    server_addr.sin_port = htons(PORT);

    if (connect(client.socket_fd, (struct sockaddr *)&server_addr, sizeof(server_addr)) < 0) {
        printf("%sErreur de connexion au serveur%s\n", RED, RESET);
        return -1;
    }

    printf("%sConnexion établie avec le serveur %s:%d%s\n", GREEN, HOST, PORT, RESET);
    return 0;
}

void envoyer_message(const char* message) {
    char buffer[BUFFER_SIZE];
    snprintf(buffer, BUFFER_SIZE, "%s\n", message);

    if (send(client.socket_fd, buffer, strlen(buffer), 0) < 0) {
        printf("%sErreur lors de l'envoi du message%s\n", RED, RESET);
        client.partie_en_cours = false;
    }
}

int recevoir_message(char* buffer, int taille) {
    int bytes_received = recv(client.socket_fd, buffer, taille - 1, 0);

    if (bytes_received > 0) {
        buffer[bytes_received] = '\0';
        return bytes_received;
    } else if (bytes_received == 0) {
        printf("%sConnexion fermée par le serveur%s\n", RED, RESET);
        client.partie_en_cours = false;
    } else {
        printf("%sErreur lors de la réception du message%s\n", RED, RESET);
        client.partie_en_cours = false;
    }

    return bytes_received;
}

void traiter_message(const char* message) {
    char buffer[BUFFER_SIZE];
    strncpy(buffer, message, BUFFER_SIZE);

    char* newline = strchr(buffer, '\n');
    if (newline) *newline = '\0';

    if (strncmp(buffer, "INIT:", 5) == 0) {
        // Format: INIT:monPseudo:adversairePseudo:monSymbole:symboleAdversaire
        char* token = strtok(buffer + 5, ":");
        if (token) strncpy(client.pseudo, token, sizeof(client.pseudo) - 1);

        token = strtok(NULL, ":");
        if (token) strncpy(client.adversaire, token, sizeof(client.adversaire) - 1);

        token = strtok(NULL, ":");
        if (token) client.symbole = token[0];

        token = strtok(NULL, ":");
        if (token) client.symbole_adversaire = token[0];

        printf("\n%sPartie démarrée contre %s%s\n", BOLD, client.adversaire, RESET);
        printf("Vous jouez avec le symbole %c\n", client.symbole);

    } else if (strcmp(buffer, "JOUER") == 0) {
        client.mon_tour = true;
        afficher_grille();
        printf("%sC'est à votre tour de jouer !%s\n", BOLD, RESET);

    } else if (strcmp(buffer, "ATTENDRE") == 0) {
        client.mon_tour = false;
        printf("En attente du coup de %s...\n", client.adversaire);

    } else if (strcmp(buffer, "REJOUER") == 0) {
        printf("%sCase occupée ou invalide, veuillez rejouer%s\n", RED, RESET);
        client.mon_tour = true;

    } else if (strncmp(buffer, "MISE_A_JOUR:", 11) == 0) {
        mettre_a_jour_grille(buffer + 11);
        afficher_grille();

    } else if (strncmp(buffer, "GAGNANT:", 8) == 0) {
        char gagnant[50];
        strncpy(gagnant, buffer + 8, sizeof(gagnant) - 1);
        gagnant[sizeof(gagnant) - 1] = '\0';
        afficher_grille();

        if (strcmp(gagnant, client.pseudo) == 0) {
            printf("\n%s%sFélicitations ! Vous avez gagné !%s\n", BOLD, GREEN, RESET);
        } else {
            printf("\n%s%s%s a gagné la partie.%s\n", BOLD, RED, gagnant, RESET);
        }
        client.partie_en_cours = false;

    } else if (strcmp(buffer, "MATCH_NUL") == 0) {
        afficher_grille();
        printf("\n%sMatch nul !%s\n", BOLD, RESET);
        client.partie_en_cours = false;

    } else if (strcmp(buffer, "ADVERSAIRE_DECONNECTE") == 0) {
        printf("\n%sVotre adversaire s'est déconnecté. Partie terminée.%s\n", RED, RESET);
        client.partie_en_cours = false;

    } else if (strncmp(buffer, "Bienvenue", 9) == 0) {
        printf("%s\n", buffer);

    } else {
        printf("Message du serveur: %s\n", buffer);
    }
}

void afficher_grille() {
    printf("\n  ");
    for (int i = 0; i < TAILLE; i++) {
        printf("%d ", i);
    }
    printf("\n");

    for (int i = 0; i < TAILLE; i++) {
        printf("%d ", i);
        for (int j = 0; j < TAILLE; j++) {
            if (client.grille[i][j] == 'X') {
                printf("%sX%s ", BLUE, RESET);
            } else if (client.grille[i][j] == 'O') {
                printf("%sO%s ", GREEN, RESET);
            } else {
                printf("· ");
            }
        }
        printf("\n");
    }
    printf("\n");
}

void mettre_a_jour_grille(const char* etat) {
    char cells[TAILLE*TAILLE][2];
    int index = 0;

    char etat_copie[BUFFER_SIZE];
    strncpy(etat_copie, etat, BUFFER_SIZE);

    char* token = strtok(etat_copie, ",");
    while (token != NULL && index < TAILLE*TAILLE) {
        cells[index][0] = token[0];
        cells[index][1] = '\0';
        index++;
        token = strtok(NULL, ",");
    }

    index = 0;
    for (int i = 0; i < TAILLE; i++) {
        for (int j = 0; j < TAILLE; j++) {
            if (index < TAILLE*TAILLE) {
                if (cells[index][0] == 'X') {
                    client.grille[i][j] = 'X';
                } else if (cells[index][0] == 'O') {
                    client.grille[i][j] = 'O';
                } else {  // "E" pour vide
                    client.grille[i][j] = ' ';
                }
                index++;
            }
        }
    }
}

void jouer_coup() {
    int x, y;
    char input[50];

    printf("%sEntrez votre coup (x,y): %s", BOLD, RESET);
    if (fgets(input, sizeof(input), stdin) == NULL) {
        client.partie_en_cours = false;
        return;
    }

    input[strcspn(input, "\n")] = 0;

    if (sscanf(input, "%d,%d", &x, &y) != 2) {
        printf("%sFormat invalide. Utilisez x,y (ex: 1,2)%s\n", RED, RESET);
        return;
    }

    if (x < 0 || x >= TAILLE || y < 0 || y >= TAILLE) {
        printf("%sCoordonnées hors limites. Elles doivent être entre 0 et %d%s\n", RED, TAILLE-1, RESET);
        return;
    }

    char coup[10];
    sprintf(coup, "%d,%d", x, y);
    envoyer_message(coup);

    client.mon_tour = false;
}

// Nettoyer le buffer stdin c'est important  = )
void nettoyer_stdin() {
    int c;
    while ((c = getchar()) != '\n' && c != EOF) { }
}

void* thread_recevoir_messages(void* arg) {
    char buffer[BUFFER_SIZE];

    while (client.partie_en_cours && running) {
        memset(buffer, 0, BUFFER_SIZE);
        if (recevoir_message(buffer, BUFFER_SIZE) > 0) {
            traiter_message(buffer);
        } else {
            break;
        }
    }

    return NULL;
}

void handle_signal(int sig) {
    printf("\n%sInterruption reçue, fermeture du client...%s\n", RED, RESET);
    running = false;
    client.partie_en_cours = false;
    close(client.socket_fd);
    exit(EXIT_SUCCESS);
}

void cleanup() {
    close(client.socket_fd);
}

int main() {
    printf("%s=== Client Morpion C pour macOS ===%s\n", BOLD, RESET);

    initialiser_client();

    if (connecter_au_serveur() != 0) {
        cleanup();
        return EXIT_FAILURE;
    }

    printf("Entrez votre pseudo: ");
    if (fgets(client.pseudo, sizeof(client.pseudo), stdin) == NULL) {
        printf("%sErreur lors de la lecture du pseudo%s\n", RED, RESET);
        cleanup();
        return EXIT_FAILURE;
    }

    client.pseudo[strcspn(client.pseudo, "\n")] = 0;

    if (strlen(client.pseudo) == 0) {
        sprintf(client.pseudo, "Joueur%d", rand() % 1000);
    }

    envoyer_message(client.pseudo);

    if (pthread_create(&thread_reception, NULL, thread_recevoir_messages, NULL) != 0) {
        printf("%sErreur lors de la création du thread de réception%s\n", RED, RESET);
        cleanup();
        return EXIT_FAILURE;
    }

    while (client.partie_en_cours && running) {
        if (client.mon_tour) {
            jouer_coup();
        } else {
            // Pause pour éviter d'utiliser trop de CPU = )
            usleep(100000);
        }
    }

    pthread_join(thread_reception, NULL);

    printf("\nFin de la partie\n");
    cleanup();

    return EXIT_SUCCESS;
}