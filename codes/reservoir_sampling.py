#Ejemplo del algoritmo de muestreo de reserva o depósito.
import random

def reservoir_sampling(stream, n, k):
    """Función para seleccionar aleatoriamente k elementos de un stream [0,1,2,.... n-1]
    reserva [] es el array de salida (contendrá los k elementos)
    """
    i=0; #indice
    """ Inicializamos reserva[] con los 1ros k elementos del stream """
    reserva = [0]*k;
    for i in range(k):
        reserva[i] = stream[i];
    #print(reserva)

    """ Iteramos sobre el k+1 elemento hacia el n elemento"""
    while(i < n):
        j = random.randrange(i+1); #tomamos un índice aleatorio de 0 hasta i
        #print("j:",j,"i:", i)
        """ Si el índice aleatorio es más pequeño que k, reemplazamos el elemento presente en el índice  con un nuevo elemento del stream"""
        if(j < k):
            reserva[j] = stream[i];
            #print(reserva, stream)
        i+=1;

    #print("Muestreo aleatorio de reserva")
    #print(reserva) #Estos son los elementos que se seleccionarian aleatoriamente

if __name__ == "__main__":
    stream = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12];
    n = len(stream); #revisamos el tamaño del flujo de datos
    k = 5; #número de elementos aleatorios que serán seleccionados
    print("Impresión del flujo de datos recibido:", stream)
    print("Número de k elementos que serán seleccionados: ", k)
    print("Tamaño del fujo: ", n)
    print("==================================================")
    reservoir_sampling(stream, n, k);
