import controller.SimuladorController;
import model.TipoAlgoritmo;

public class Main {
    public static void main(String[] args) {
        SimuladorController controller = new SimuladorController();

        // Parâmetros: algoritmo, quantum, ms por ciclo, processos iniciais
        controller.iniciarSimulacao(
            TipoAlgoritmo.ROUND_ROBIN, 
            2,          // quantum
            800,        // ms por ciclo de clock
            2           // carga inicial
        );
    }
}