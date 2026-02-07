import numpy as np
from scipy.spatial import cKDTree
from typing import Dict, Tuple, Optional, Any


def allocate_knn_greedy(
    active_drivers: Dict[Any, Tuple[float, float]],
    active_passengers: Dict[Any, Tuple[float, float]],
    k: int = 20,
    max_dist: Optional[float] = None,
) -> Dict[Any, Optional[Any]]:
    '''
    Fast kNN-based greedy bipartite allocation between drivers and passengers.

    This method replaces the cubic-time Hungarian algorithm with a scalable,
    approximate approach suitable for real-time mobility-on-demand systems.

    Algorithm overview
    ------------------
    1. Build a KD-tree on passenger GPS locations.
    2. For each driver, query its k nearest passenger candidates.
    3. Greedily assign the closest available passenger.
    4. Each passenger can be matched to at most one driver.
    5. If no feasible passenger exists, the driver remains unmatched (None).

    Notes
    -----
    - This algorithm is NOT globally optimal.
    - Time complexity is approximately O(n log n).
    - In practice, this performs very well for large-scale fleets.

    Parameters
    ----------
    active_drivers : dict
        Mapping {driver_id: (latitude, longitude)}.
    active_passengers : dict
        Mapping {passenger_id: (latitude, longitude)}.
    k : int, optional
        Number of nearest passenger candidates to consider per driver.
        Typical values are between 10 and 50.
    max_dist : float or None, optional
        Optional distance cutoff (same units as coordinates).
        If provided, passengers beyond this distance are ignored.

    Returns
    -------
    allocation : dict
        Mapping {driver_id: passenger_id} if matched,
        or {driver_id: None} if unmatched.
    '''
    # Extract IDs for stable indexing
    driver_ids = list(active_drivers.keys())
    passenger_ids = list(active_passengers.keys())

    # Initialize allocation: unmatched by default
    allocation: Dict[Any, Optional[Any]] = {d: None for d in driver_ids}

    nD, nP = len(driver_ids), len(passenger_ids)
    if nD == 0 or nP == 0:
        return allocation

    # Convert GPS coordinates to NumPy arrays
    D = np.asarray(
        [active_drivers[d] for d in driver_ids], dtype=float
    )  # shape: (nD, 2)

    P = np.asarray(
        [active_passengers[p] for p in passenger_ids], dtype=float
    )  # shape: (nP, 2)

    # Limit k by number of passengers
    k_eff = min(k, nP)

    # Build KD-tree on passenger locations
    tree = cKDTree(P)

    # Query k nearest passengers for all drivers (vectorized)
    dists, idxs = tree.query(D, k=k_eff, workers=-1)

    # Ensure 2D arrays even when k_eff == 1
    if k_eff == 1:
        dists = dists[:, None]
        idxs = idxs[:, None]

    # Track which passengers have already been assigned
    taken = np.zeros(nP, dtype=bool)

    # Optional improvement: assign drivers with fewer feasible options first
    if max_dist is not None:
        feasible_counts = (dists <= max_dist).sum(axis=1)
        driver_order = np.argsort(feasible_counts)
    else:
        driver_order = np.arange(nD)

    # Greedy matching
    for i in driver_order:
        for dist, j in zip(dists[i], idxs[i]):
            if max_dist is not None and dist > max_dist:
                break
            if not taken[j]:
                taken[j] = True
                allocation[driver_ids[i]] = passenger_ids[j]
                break

    return allocation


def random_points_singapore(n: int, seed: int = 0) -> Dict[str, Tuple[float, float]]:
    '''
    Generate random GPS points within a rough bounding box of Singapore.

    This is useful for stress-testing matching algorithms without
    requiring road-network or land-use data.

    Parameters
    ----------
    n : int
        Number of points to generate.
    seed : int, optional
        Random seed for reproducibility.

    Returns
    -------
    points : dict
        Mapping {id_i: (latitude, longitude)}.
    '''
    rng = np.random.default_rng(seed)

    # Approximate Singapore bounding box
    lat_min, lat_max = 1.15, 1.47
    lon_min, lon_max = 103.60, 104.10

    return {
        f"id_{i}": (
            float(rng.uniform(lat_min, lat_max)),
            float(rng.uniform(lon_min, lon_max)),
        )
        for i in range(n)
    }


if __name__ == "__main__":
    import time

    # Single snapshot test
    n = 1000
    active_drivers = random_points_singapore(n, seed=45)
    
    active_passengers = random_points_singapore(n, seed=40)

    t0 = time.perf_counter()
    allocation = allocate_knn_greedy(
        active_drivers,
        active_passengers,
        k=20,
        max_dist=None,   # e.g., 0.01 ≈ 1 km (roughly)
    )
    t1 = time.perf_counter()

    matched = sum(p is not None for p in allocation.values())

    print("Singapore random GPS test")
    print("-" * 40)
    print(f"Drivers   : {n}")
    print(f"Passengers: {n}")
    print(f"Matched   : {matched}")
    print(f"Runtime   : {(t1 - t0) * 1000:.2f} ms")

