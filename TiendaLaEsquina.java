import java.util.Scanner;

public class TiendaLaEsquina {

    public static void main(String[] args) {
        double valorFactura = ingresarRealD("Ingrese valor total de la factura: ");
        double dineroEntregado = ingresarRealD("Ingrese valor dinero entregado por el cliente: ");
        double devuelta = calcularDevuelta(dineroEntregado, valorFactura);
        generarMensaje(valorFactura, dineroEntregado, devuelta);
    }

    public static double ingresarRealD(String mensaje) {
        Scanner entrada = new Scanner(System.in);
        System.out.print(mensaje);
        return entrada.nextDouble();
    }

    public static double calcularDevuelta(double dineroEntregado, double valorFactura) {
        double devuelta = dineroEntregado - valorFactura;
        return devuelta;
    }

    public static void generarMensaje(double valorFactura, double dineroEntregado, double devuelta) {
        String mensaje = "Valor total de la factura: $" + valorFactura +
                "\nDinero recibido: $" + dineroEntregado +
                "\nDevuelta a entregar: $" + devuelta;
        System.out.println(mensaje);
    }
}
