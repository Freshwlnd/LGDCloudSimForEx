# Lattice: An Efficient Task Placement Framework for Geographically Distributed Clouds

Welcome to the Lattice repository. This repository contains the experimental data, algorithm implementations, and experimental environment for our research paper. We aim to provide all necessary resources for replicating and understanding our experiments.

## Repository Structure
```
Lattice/
│
├── ExDataForLattice
│   ├── RecordDb
│   │   └── # Simulated experiment result database
│   ├── Log
│   │   └── # Simulation experiment logs
│   └── FinalResult
│       └── # Consolidated experimental data
│
├── LGDCloudSim
│   └── # LGDCloudSim large-scale simulator code, including algorithm implementations and experimental environment
│
└── README.md
└── # This documentation
```

## Getting Started

### Prerequisites

Ensure you have the following prerequisites installed on your system:

- For executing `./LGDCloudSim`:
  - Apache Maven 4.0.0
  - Java 17.0.10

- For executing `./LGDCloudSim/scripts/dataSolve.py`:
  - Python 3.12.2
  - Python packages: `os`, `sqlite3`, `csv`, `copy`

### Execution

To run the simulation, navigate to the `./LGDCloudSim/scripts` directory and execute the following command:

```sh
nohup ./Lattice.sh &
```
This will start the simulation in the background and log the output to a file.

## Data Description

### ExDataForLattice

- **RecordDb**: Contains the database of simulated experiment results.
- **Log**: Includes logs from the simulation experiments.
- **FinalResult**: Provides the consolidated experimental data used in our analysis.

### LGDCloudSim

This directory contains the source code for the [LGDCloudSim simulator](https://github.com/slipegg/LGDCloudSim), including all algorithm implementations and the experimental environment setup used in our research.

## Contributing

We welcome contributions to improve this project. Please fork the repository and submit a pull request with your changes.

## License

This project is licensed under the GNU GPLv3 License - see the [GNU GPLv3](http://www.gnu.org/licenses/gpl-3.0) file for details.
