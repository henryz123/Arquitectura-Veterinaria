import { CurrencyPipe, NgFor, NgIf } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { finalize, forkJoin } from 'rxjs';
import {
  CatalogCategory,
  CatalogCategoryStatus,
  CatalogProduct,
  CatalogProductStatus,
  StockBalance,
  StockMovement,
} from '../../core/api/api.models';
import { CatalogApiService } from '../../core/api/catalog-api.service';
import { AuthStateService } from '../../core/auth/auth-state.service';

const PRODUCT_STATUS_LABELS: Record<CatalogProductStatus, string> = {
  ACTIVO_PUBLICADO: 'Activo y publicado',
  ACTIVO_NO_PUBLICADO: 'Activo interno',
  INACTIVO: 'Inactivo',
};

@Component({
  selector: 'app-inventory-catalog-page',
  imports: [CurrencyPipe, NgFor, NgIf, ReactiveFormsModule, RouterLink],
  templateUrl: './inventory-catalog-page.component.html',
  styleUrl: './inventory-catalog-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class InventoryCatalogPageComponent implements OnInit {
  private readonly api = inject(CatalogApiService);
  private readonly auth = inject(AuthStateService);
  private readonly formBuilder = inject(FormBuilder);

  readonly context = this.auth.context;
  readonly categories = signal<CatalogCategory[]>([]);
  readonly products = signal<CatalogProduct[]>([]);
  readonly stockBalances = signal<StockBalance[]>([]);
  readonly stockMovements = signal<StockMovement[]>([]);
  readonly selectedCategory = signal<CatalogCategory | null>(null);
  readonly selectedProduct = signal<CatalogProduct | null>(null);
  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly error = signal<string | null>(null);
  readonly notice = signal<string | null>(null);
  readonly categorySubmitted = signal(false);
  readonly productSubmitted = signal(false);
  readonly stockSubmitted = signal(false);
  readonly canManageCatalog = computed(() => {
    const context = this.context();
    return context?.accountType === 'STAFF' && context.roles.includes('ADMIN_VETERINARIA');
  });
  readonly activeCategories = computed(() => this.categories().filter((category) => category.status === 'ACTIVA'));
  readonly inventoryProducts = computed(() => this.products().filter((product) => product.inventoryManaged));
  readonly categoryStatusOptions: ReadonlyArray<{ value: CatalogCategoryStatus; label: string }> = [
    { value: 'ACTIVA', label: 'Activa' },
    { value: 'INACTIVA', label: 'Inactiva' },
  ];
  readonly productStatusOptions: ReadonlyArray<{ value: CatalogProductStatus; label: string }> = [
    { value: 'ACTIVO_NO_PUBLICADO', label: 'Activo interno' },
    { value: 'ACTIVO_PUBLICADO', label: 'Activo y publicado' },
    { value: 'INACTIVO', label: 'Inactivo' },
  ];
  readonly filterForm = this.formBuilder.nonNullable.group({
    query: ['', Validators.maxLength(160)],
    status: ['' as '' | CatalogProductStatus],
    categoryId: [''],
  });
  readonly categoryForm = this.formBuilder.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(120)]],
    status: ['ACTIVA' as CatalogCategoryStatus, Validators.required],
  });
  readonly productForm = this.formBuilder.nonNullable.group({
    categoryId: [''],
    sku: ['', [Validators.required, Validators.maxLength(64)]],
    title: ['', [Validators.required, Validators.maxLength(160)]],
    description: ['', Validators.maxLength(2000)],
    salePricePen: [0, [Validators.required, Validators.min(0)]],
    status: ['ACTIVO_NO_PUBLICADO' as CatalogProductStatus, Validators.required],
    inventoryManaged: [true],
    imageUrl: ['', Validators.maxLength(500)],
  });
  readonly stockForm = this.formBuilder.nonNullable.group({
    productId: ['', Validators.required],
    quantityDelta: [0, [Validators.required, Validators.pattern(/^-?\d+$/), Validators.min(-1000000), Validators.max(1000000)]],
    reason: ['', [Validators.required, Validators.minLength(3), Validators.maxLength(500)]],
  });

  ngOnInit(): void {
    if (!this.canManageCatalog()) {
      this.loading.set(false);
      this.error.set('Tu sesión no está autorizada para gestionar el catálogo. El servidor también rechaza esta operación.');
      return;
    }
    this.reload();
  }

  reload(): void {
    if (!this.canManageCatalog()) {
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    const filters = this.filterForm.getRawValue();
    forkJoin({
      categories: this.api.listCategories(),
      products: this.api.listProducts(filters.query, filters.status || undefined, filters.categoryId || undefined),
      stockBalances: this.api.listStock(),
      stockMovements: this.api.listStockMovements(),
    }).subscribe({
      next: ({ categories, products, stockBalances, stockMovements }) => {
        this.categories.set(categories.data);
        this.products.set(products.data);
        this.stockBalances.set(stockBalances.data);
        this.stockMovements.set(stockMovements.data);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.error.set('No fue posible cargar el catálogo. Actualiza la página para revalidar la sesión y el tenant.');
      },
    });
  }

  submitCategory(): void {
    this.categorySubmitted.set(true);
    if (this.categoryForm.invalid || this.saving()) {
      this.categoryForm.markAllAsTouched();
      return;
    }
    this.beginSave();
    const draft = { ...this.categoryForm.getRawValue(), name: this.categoryForm.controls.name.value.trim() };
    const selected = this.selectedCategory();
    const request = selected ? this.api.updateCategory(selected.id, draft) : this.api.createCategory(draft);
    request.pipe(finalize(() => this.saving.set(false))).subscribe({
      next: () => {
        this.resetCategoryForm();
        this.notice.set(selected ? 'La categoría fue actualizada.' : 'La categoría fue registrada en esta veterinaria.');
        this.reload();
      },
      error: () => this.error.set('No fue posible guardar la categoría. Verifica que su nombre sea único dentro de la veterinaria.'),
    });
  }

  submitProduct(): void {
    this.productSubmitted.set(true);
    if (this.productForm.invalid || this.saving()) {
      this.productForm.markAllAsTouched();
      return;
    }
    const raw = this.productForm.getRawValue();
    if (!Number.isFinite(raw.salePricePen) || Math.round(raw.salePricePen * 100) !== raw.salePricePen * 100) {
      this.error.set('El precio debe ser un importe PEN no negativo con hasta dos decimales.');
      return;
    }
    this.beginSave();
    const draft = {
      ...raw,
      categoryId: raw.categoryId || null,
      sku: raw.sku.trim(),
      title: raw.title.trim(),
      description: raw.description.trim() || null,
      imageUrl: raw.imageUrl.trim() || null,
    };
    const selected = this.selectedProduct();
    const request = selected ? this.api.updateProduct(selected.id, draft) : this.api.createProduct(draft);
    request.pipe(finalize(() => this.saving.set(false))).subscribe({
      next: () => {
        this.resetProductForm();
        this.notice.set(selected ? 'El producto fue actualizado.' : 'El producto fue registrado en el catálogo del tenant.');
        this.reload();
      },
      error: () => this.error.set('No fue posible guardar el producto. Revisa el SKU, la categoría y el estado seleccionado.'),
    });
  }

  editCategory(category: CatalogCategory): void {
    this.selectedCategory.set(category);
    this.categorySubmitted.set(false);
    this.categoryForm.setValue({ name: category.name, status: category.status });
  }

  editProduct(product: CatalogProduct): void {
    this.selectedProduct.set(product);
    this.productSubmitted.set(false);
    this.productForm.setValue({
      categoryId: product.categoryId ?? '', sku: product.sku, title: product.title, description: product.description ?? '',
      salePricePen: product.salePricePen, status: product.status, inventoryManaged: product.inventoryManaged,
      imageUrl: product.imageUrl ?? '',
    });
  }

  resetCategoryForm(): void {
    this.selectedCategory.set(null);
    this.categorySubmitted.set(false);
    this.categoryForm.reset({ name: '', status: 'ACTIVA' });
  }

  resetProductForm(): void {
    this.selectedProduct.set(null);
    this.productSubmitted.set(false);
    this.productForm.reset({
      categoryId: '', sku: '', title: '', description: '', salePricePen: 0,
      status: 'ACTIVO_NO_PUBLICADO', inventoryManaged: true, imageUrl: '',
    });
  }

  submitStockAdjustment(): void {
    this.stockSubmitted.set(true);
    if (this.stockForm.invalid || this.saving()) {
      this.stockForm.markAllAsTouched();
      return;
    }
    const raw = this.stockForm.getRawValue();
    if (raw.quantityDelta === 0 || !Number.isInteger(raw.quantityDelta)) {
      this.error.set('La cantidad debe ser una unidad entera distinta de cero.');
      return;
    }
    this.beginSave();
    this.api.adjustStock(raw.productId, {
      quantityDelta: raw.quantityDelta,
      reason: raw.reason.trim(),
      operationId: crypto.randomUUID(),
    }).pipe(finalize(() => this.saving.set(false))).subscribe({
      next: ({ data }) => {
        this.stockSubmitted.set(false);
        this.stockForm.reset({ productId: '', quantityDelta: 0, reason: '' });
        this.notice.set(`Movimiento ${data.movement.movementType} registrado. Saldo actual: ${data.balance.availableQuantity}.`);
        this.reload();
      },
      error: () => this.error.set('No fue posible registrar el movimiento. Verifica el producto, el motivo y el saldo disponible.'),
    });
  }

  movementLabel(type: StockMovement['movementType']): string {
    return {
      ALTA_INICIAL: 'Saldo inicial', AJUSTE_POSITIVO: 'Ajuste positivo', AJUSTE_NEGATIVO: 'Ajuste negativo',
      VENTA_POS: 'Venta presencial', CONFIRMACION_ONLINE: 'Confirmacion online', CANCELACION_ONLINE: 'Cancelacion online',
      DEVOLUCION_APTA: 'Devolucion apta', RECONCILIACION: 'Reconciliacion',
    }[type];
  }

  productStatusLabel(status: CatalogProductStatus): string {
    return PRODUCT_STATUS_LABELS[status];
  }

  categoryStatusLabel(status: CatalogCategoryStatus): string {
    return status === 'ACTIVA' ? 'Activa' : 'Inactiva';
  }

  trackById(_: number, entity: { id: string }): string {
    return entity.id;
  }

  private beginSave(): void {
    this.saving.set(true);
    this.error.set(null);
    this.notice.set(null);
  }
}
